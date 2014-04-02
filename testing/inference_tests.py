#!/usr/bin/env python

import os.path
from os.path import join
import argparse
import re
import subprocess
import shutil

# inference_tests.py looks for test files
# in a set of predefined folders, excutes them,
# and aggregates successes and failures.
#
# It has a gold mode to compare roundtripped java files
# against gold files. This is to test jaif insertion and
# @VarAnnot creation. gold-update updates gold files
# with the roundtripped java files.
#

# TODO: cleanup duplication of code

MODES=['infer', 'typecheck', 'roundtrip',
    'roundtrip-typecheck', 'gold', 'gold-update']

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', default='typecheck', help='Inference test modes: [%s]' % ', '.join(MODES))
    parser.add_argument('-t', '--test', help='Regex to match test names on.')
    parser.add_argument('-d', '--debug', action='store_true', help='Print out all command output')
    parser.add_argument('-a', '--args', default="", help='verigames.py args')
    parser.add_argument('--checker', default='ostrusted.OsTrustedChecker', help='Type system to run')
    args = parser.parse_args()

    execute_tests(args.checker, args.test, args.mode, args.debug, args.args)

def execute_tests(checker, test_name, mode, debug, args):
    pattern = re.compile(test_name) if test_name else None

    if mode == 'gold' or mode == 'gold-update':
        run_gold(mode, pattern, checker, args, debug)
    else:
        run_tests(mode, pattern, checker, args, debug)

def run_tests(mode, pattern, checker, args, debug):
    """ Execute tests using the specified mode """
    print 'Searching directories: ', get_search_dirs()
    test_files = find_test_files(get_search_dirs(), pattern)
    if mode == 'typecheck':
        test_files = [f for f in test_files
                    if 'Basic.java' not in f
                    and 'Simple.java' not in f]

    successes = []
    failures = []
    for test_file in test_files:
        print 'Executing test ' + test_file
        cmd = make_inference_cmd(checker, test_file, mode, args)
        success = execute(cmd, debug)
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

def run_gold(mode, pattern, checker, args, debug):
    """ Execute tests and compare them against gold files. """
    # Create list of test files
    print 'Searching directories: ', get_search_dirs()
    test_files = find_test_files(get_common_dirs(), pattern)
    # Set the output dir so we know where to diff
    args += ' --output-dir=' + join(get_script_dir(), 'output')

    successes = []
    failures = []
    gold_differs = []
    # Generate and insert jaif for each file
    for test_file in test_files:
        print 'Executing test ' + test_file
        cmd = make_inference_cmd(checker, test_file, 'roundtrip', args)
        success = execute(cmd, debug)
        if success:
            print 'Infer command success'

            updated_file = join(get_script_dir(), 'output',
                    os.path.basename(test_file))
            gold_file = join(get_script_dir(), 'common_gold',
                    os.path.basename(test_file))
            if mode == 'gold-update':
                print 'Updating gold file:', gold_file
                shutil.copyfile(updated_file, gold_file)
                successes.append(test_file)

            else:
                print 'Diffing output with gold file'
                success = execute('/usr/bin/diff %s %s ' \
                        % (updated_file, gold_file), debug)
                if not success:
                    print 'Failure: gold files differed'
                    gold_differs.append(test_file)
                else:
                    print 'Success: gold files match'
                    successes.append(test_file)
        else:
            print 'Failure: inference.py error'
            failures.append(test_file)

        print ""

    print '%d Passed, %d Gold mismatch, %d inference.py failure' \
            % (len(successes), len(gold_differs), len(failures))
    print 'Gold mismatches:'
    for path in gold_differs:
        print path
    print 'inference.py failures:'
    for failed in failures:
        print failed

def find_test_files(dirs, pattern=None):
    test_files = [join(test_dir, test_file)
        for test_dir in get_common_dirs()
            for test_file in os.listdir(test_dir)
                if os.path.isfile(join(test_dir, test_file))
                    and test_file.endswith('.java')
                    and (pattern is None or pattern.search(test_file))]
    return test_files


def make_inference_cmd(checker, file_name, mode, args):
    return '%s %s --mode %s --checker %s %s' % \
            (get_inference_exe(), args, mode, checker, file_name)

def get_search_dirs():
    dirs = []
    checkersdir = os.environ['CHECKERS_TESTS'] if 'CHECKERS_TESTS' in os.environ else os.environ['CHECKER_FRAMEWORK']
    dirs.append(join(checkersdir, 'checker', 'tests', 'all-systems'))
#    dirs.append(join(get_script_dir(), 'encrypted'))
    dirs += get_common_dirs()
    return dirs

def get_common_dirs():
    dirs = []
    dirs.append(join(get_script_dir(), 'common'))
    dirs.append(join(get_script_dir(), 'common', 'refmerge'))
    dirs.append(join(get_script_dir(), 'common', 'generics'))
    return dirs

def get_script_dir():
    return os.path.dirname(os.path.abspath(__file__))

def get_inference_exe():
    return os.path.abspath(join(get_script_dir(), '../scripts/inference.py'))

def execute(args, debug):
    print "Executing" , args
    if debug:
        ret = subprocess.call(args, shell=True)
    else:
        with open(os.devnull) as out:
            ret = subprocess.call(args, shell=True, stdout=out, stderr=out)
    return ret == 0

if __name__=='__main__':
   main()
