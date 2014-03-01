#!/usr/bin/env python

from os.path import join
import argparse
import os.path
import re
import subprocess

MODES=['typecheck', 'roundtrip', 'xmlsolve', 'xml-roundtrip']

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', default='typecheck', help='Inference test modes: [%s' % ', '.join(MODES))
    parser.add_argument('-t', '--test', help='Regex to match test names on.')
    parser.add_argument('-d', '--debug', action='store_true', help='Print out all command output')
    parser.add_argument('-a', '--args', default="", help='verigames.py args')
    parser.add_argument('--checker', default='ostrusted.OsTrustedChecker', help='Type system to run')
    args = parser.parse_args()

    execute_tests(args.checker, args.test, args.mode, args.debug, args.args)

def execute_tests(checker, test_name, mode, debug, args):
    pattern = re.compile(test_name) if test_name else None
    print 'build_search_dirs', build_search_dirs()
    test_files = [join(test_dir, test_file)
            for test_dir in build_search_dirs()
                for test_file in os.listdir(test_dir)
                    if os.path.isfile(join(test_dir, test_file))
                    and test_file.endswith('.java')
                    and (pattern is None or pattern.search(test_file))]

    if mode == 'typecheck':
        test_files = [f for f in test_files
                    if 'Basic.java' not in f
                    and 'Simple.java' not in f]

    successes = []
    failures = []
    for test_file in test_files:
        print 'Executing test ' + test_file
        cmd = get_inference_cmd(checker, test_file, mode, args)
        success = execute_command(cmd, debug)
        if success:
            print 'Success'
            successes.append(test_file)
        else:
            print 'Failure'
            failures.append(test_file)

    print_summary(successes, failures)

def print_summary(successes, failures):
    print '%d Passed, %d failed' % (len(successes), len(failures))
    print 'Failed tests:'
    for failed in failures:
        print failed

def get_inference_cmd(checker, file_name, mode, args):
    return '%s %s --mode %s --checker %s %s' % \
            (get_inference_exe(), args, mode, checker, file_name)

def build_search_dirs():
    dirs = []
    checkersdir = os.environ['CHECKERS_TESTS'] if 'CHECKERS_TESTS' in os.environ else os.environ['CHECKERS']
    dirs.append(join(checkersdir, 'tests', 'all-systems'))
#    dirs.append(join(get_script_dir(), 'encrypted'))
    dirs.append(join(get_script_dir(), 'examples'))
    dirs.append(join(get_script_dir(), 'examples', 'refmerge'))
    dirs.append(join(get_script_dir(), 'examples', 'generics'))
    return dirs

def get_script_dir():
    return os.path.dirname(os.path.abspath(__file__))

def get_inference_exe():
    return os.path.abspath(join(get_script_dir(), '../scripts/inference.py'))

def execute_command(args, debug):
    print "Executing" , args
    if debug:
        ret = subprocess.call(args, shell=True)
    else:
        with open(os.devnull) as out:
            ret = subprocess.call(args, shell=True, stdout=out, stderr=out)
    return ret == 0

if __name__=='__main__':
   main()
