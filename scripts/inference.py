#!/usr/bin/env python

import subprocess
import argparse
import sys
import os.path
import shutil

INFERENCE_HOME = os.environ['CHECKER_INFERENCE']
JAVA_HOME = os.environ['JAVA_HOME']
AFU_HOME = os.environ.get('AFU_HOME')

# Program constants
MODES = 'infer typecheck floodsolve flood-roundtrip'.split()
AUTOMATIC_SOLVER = 'checkers.inference.floodsolver.FloodSolver'
DEBUG_OPTS = '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005'
OUTPUT_DIR = './output'
LOGBACK_LOG_LEVELS = 'OFF ERROR WARN INFO DEBUG TRACE ALL'.split()

class Checker():
    def __init__(self, name, solver=None, subanno=None, superanno=None):
        self.name = name
        self.solver = solver
        self.subanno = subanno
        self.superanno = superanno

    def update_if_set(self, solver=None):
        if solver:
            self.solver = solver

    def to_checker_args(self):
        args = 'checkers.inference.InferenceCli --checker ' + self.name
        if self.solver:
            args += ' --solver ' + self.solver

        return args

nninf_checker = Checker('nninf.copy.NninfChecker',  solver='nninf.copy.NninfGameSolver')
trusted_checker = Checker('trusted.TrustedChecker', solver='trusted.TrustedGameSolver')
encrypted_checker = Checker('encrypted.EncryptedChecker', solver='trusted.TrustedGameSolver',
    subanno='@encrypted.quals.Encrypted', superanno='@encrypted.quals.Plaintext')
ostrusted_checker = Checker('ostrusted.OsTrustedChecker', solver='trusted.TrustedGameSolver',
    subanno='@ostrusted.quals.OsTrusted', superanno='@ostrusted.quals.OsUntrusted')

checkers={nninf_checker.name: nninf_checker,
        trusted_checker.name: trusted_checker,
        encrypted_checker.name: encrypted_checker,
        ostrusted_checker.name: ostrusted_checker
        }

def error(msg):
    print >> sys.stderr, msg
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser('Execute inference on the command line.')
    parser.add_argument('--stubs', help='Stub files to use.')
    parser.add_argument('--checker', help='Typesystem Checker.')
    parser.add_argument('--debug', action='store_true', help='Listen for java debugger.')
    parser.add_argument('--extra-classpath', help='Additional classpath entries.')
    parser.add_argument('--java-args', help='Additional java args to pass in.')
    parser.add_argument('--mode', default='infer', help='Choose an inference mode from [%s].' % ', '.join(MODES))
    parser.add_argument('--log-level', default='INFO', help='Choose a log level from [%s].' % ', '.join(LOGBACK_LOG_LEVELS))
    parser.add_argument('--steps', default='', help='Manually list steps to run.')
    parser.add_argument('--not-strict', action='store_true', help='Disable some checks on generation.')
    parser.add_argument('--output_dir', default=OUTPUT_DIR, help='Directory to output artifacts during roundtrip (inference.jaif, annotated file sourc file')
    parser.add_argument('--print-world', action='store_true', help='Print debugging constraint output.')
    parser.add_argument('--prog-args', help='Additional args to pass in to program eg -AprintErrorStack.')
    parser.add_argument('--solver', help='Inference Solver. Typesystem dependent.')
    parser.add_argument('--xmx', default='2048m', help='Java max heap size.')
    parser.add_argument('-p', '--print-only', action='store_true', help='Print command to execute (but do not run).')
    parser.add_argument('files', metavar='PATH', nargs='+', help='Source files to run inference on')
    args = parser.parse_args()

    if args.mode not in MODES:
        error('Mode: %s not in allowed modes: %s' % (args.mode, MODES))

    if args.log_level not in LOGBACK_LOG_LEVELS:
        error('log-level: %s not in allowed log-levels: %s' % (args.log_level, LOGBACK_LOG_LEVELS))

    if args.mode == 'typecheck' and ( args.solver ):
        error('Solver is unused in typecheck mode.')

    # Modes are shortcuts for pipeline steps
    # Only support one order at the moment
    # MODES = 'infer typecheck floodsolve flood-roundtrip'.split()
    pipeline = []
    if args.steps:
        pipeline = args.steps.split(',')
    else:
        if args.mode == 'typecheck':
            pipeline = ['typecheck']
        elif args.mode == 'infer':
            pipeline = ['generate']
        elif args.mode == 'floodsolve':
            pipeline = ['floodsolve']
        elif args.mode == 'flood-roundtrip':
            pipeline = ['floodsolve', 'insert-jaif', 'typecheck']

    # Setup some globaly useful stuff
    classpath = get_inference_classpath()

    if args.extra_classpath:
        classpath += ':' + args.extra_classpath

    if checkers[args.checker]:
        checker = checkers[args.checker]
        checker.update_if_set( args.solver )
    else:
        checker = Checker(args.checker, args.solver)

    # State variable need to communicate between steps
    state = {'files' : args.files}

    # Execute steps
    while len(pipeline):
        step = pipeline.pop(0)
        print '\n====Executing step ' + step
        if step == 'generate':
            execute(args, generate_checker_cmd(checker, args.java_args, classpath, args.log_level,
                    args.debug, args.not_strict, args.xmx, args.print_world, args.prog_args, args.stubs, args.files))
        elif step == 'typecheck':
            execute(args, generate_typecheck_cmd(checker, args.java_args, classpath,
                    args.debug, args.not_strict, args.xmx, args.prog_args, args.stubs, state['files']))
        elif step == 'floodsolve':
            checker.solver = AUTOMATIC_SOLVER
            execute(args, generate_checker_cmd(checker, args.java_args, classpath, args.log_level,
                    args.debug, args.not_strict, args.xmx, args.print_world, args.prog_args, args.stubs, args.files))

            # Save jaif file
            if not args.print_only:
                if not os.path.exists(args.output_dir) and not args.print_only:
                    os.mkdir(args.output_dir)
                shutil.copyfile('inference.jaif', pjoin(args.output_dir, 'inference.jaif'))

            state['files'] = [pjoin(args.output_dir, os.path.basename(f)) for f in args.files]
#        elif step == 'insert-jaif':
#            # inference.jaif needs to be in output dir
#            execute(args, generate_afu_command(args.files, args.output_dir))
#
#        elif step == 'update-jaif':
#            execute(args,generate_jaif_cmd(checker, args.java_args, classpath, args.debug,
#                    world_xml_solution, 'inference.jaif', 'updatedInference.jaif'))
#            if not args.print_only:
#                if not os.path.exists(args.output_dir):
#                    os.mkdir(args.output_dir)
#                os.rename('updatedInference.jaif', pjoin(args.output_dir, 'inference.jaif'))
        else:
            print 'UNKNOWN STEP'

def generate_afu_command(files, outdir):
    files = [os.path.abspath(f) for f in files]
    insert_path = 'insert-annotations-to-source' if not AFU_HOME \
            else pjoin(AFU_HOME, 'annotation-file-utilities/scripts/insert-annotations-to-source')
    args = '%s -v -d %s %s %s ' % (insert_path, outdir, pjoin(outdir, 'inference.jaif'), ' '.join(files))
    return args

def generate_checker_cmd(checker, java_args, classpath, log_level, debug, not_strict, xmx, print_world, prog_args, stubs, files):
    java_path = pjoin(JAVA_HOME, 'bin/java')
    java_args = java_args if java_args else ''
    prog_args = prog_args if prog_args else ''
    print( java_args )
    print( xmx )
    print( classpath )
    java_opts = '%s -Xms512m -Xmx%s -Xbootclasspath/p:%s -ea ' % \
        (java_args, xmx, classpath)
    if debug:
        java_opts += ' ' + DEBUG_OPTS
    if stubs:
        prog_args += ' --stubs ' + stubs
    prog_args += ' --log-level ' + log_level
    args = ' '.join([java_path, java_opts, checker.to_checker_args(), prog_args, ' '.join(files)])
    return args

def generate_typecheck_cmd(checker, java_args, classpath, debug, not_strict,
            xmx, prog_args, stubs, files):

    java_path = pjoin(JAVA_HOME, 'bin/java')
    java_args = java_args if java_args else ''
    prog_args = prog_args if prog_args else ''
    java_opts = '%s -Xms512m -Xmx%s -jar %s -cp %s ' % \
        (java_args, xmx, get_checker_jar(), classpath)
    if debug:
        java_opts += ' -J' + DEBUG_OPTS
    if not_strict:
        java_opts += ' -DSTRICT=false '
    if stubs:
        prog_args += ' -Astubs=' + stubs
    args = ' '.join([java_path, java_opts, '-processor ', checker.name, prog_args, ' '.join(files)])
    return args

# xml_file, jaif_file, output_file, subtype_annotation, supertype_annotation
def generate_jaif_cmd(checker, java_args, classpath, debug, xml_file, jaif_file, output_file):
    java_path = pjoin(JAVA_HOME, 'bin/java')
    java_args = java_args if java_args else ''
    java_opts = '%s -cp %s ' % (java_args, classpath)
    if debug:
        java_opts += ' -J' + DEBUG_OPTS
    args = ' '.join([java_path, java_opts, 'verigames.utilities.JAIFParser', xml_file, jaif_file, \
            output_file, checker.subanno, checker.superanno])
    return args

def execute(cli_args, args, check_return=True):
    if cli_args.print_only:
        print('Would have executed command: \n' + args)
        print
    else:
        print('Executing command: \n' + args)
        print
        ret = subprocess.call(args, shell=True)
        if check_return and ret:
            error('Command exited with unexpected status code: %d' % ret)
        return ret

def get_checker_jar():
    return pjoin(INFERENCE_HOME, 'dist/checkers.jar')

def get_inference_classpath():
    base_dir = pjoin(INFERENCE_HOME, 'dist')
    return get_classpath(base_dir)

def pjoin(*parts):
    return os.path.join(*[os.path.join(part) for part in parts])

def get_classpath(base_dir):
    if not os.path.isdir(base_dir):
        error('Inference dist directory not found: %s' % base_dir)
    jars = [os.path.join(base_dir, f) for f in os.listdir(base_dir)
                if os.path.isfile(os.path.join(base_dir, f))
                and f.endswith('.jar')]
    jars.reverse()
    return ':'.join(jars)

def error(msg):
    print >> sys.stderr, msg
    print >> sys.stderr, 'Exiting'
    sys.exit(1)

if __name__=='__main__':
    main()
