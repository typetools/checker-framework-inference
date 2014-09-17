#!/usr/bin/env python

from os import path
import argparse
import inference
from inference  import *

class DictWrapper:
    def __init__(self, adict):
        self.__dict__.update(adict)

def epilog():
    return '''typesystem.py takes all of the same arguments as inference.py followed by a short checker option and java files.
           Using the checker option, typesystem.py first sets the checker, stubs, and any other option useful for the specified
           checker.  It then applies all of the remaining options from the command line, overriding any options
           that were previously set.'''

def description():
    return '''typesystem.py executes inference.py with the correct settings for a single checker.'''

def main():
    script_dir = path.dirname(path.realpath(__file__))
    checkers_base = path.join(script_dir, '..', 'src')

    checker_to_settings = {
        'Hardcoded' : {
            'checker'  : 'hardcoded.HardcodedChecker',
            'stubs'    : path.realpath(path.join(checkers_base, 'hardcoded', 'jdk.astub'))
        },

        'Nullness' : {
            'checker'  : 'nninf.NninfChecker'
        },

        'OsTrusted' : {
            'checker'  : 'ostrusted.OsTrustedChecker',
            'stubs'    : path.realpath(path.join(checkers_base, 'ostrusted', 'jdk.astub'))
        },

        'Sink' : {
            'checker'  : 'sparta.checkers.SpartaSinkChecker',
            'solver'   : 'sparta.checkers.SpartaSinkSolver',
            'stubs'    : path.realpath(path.join(checkers_base, 'sparta', 'checkers', 'information_flow.astub'))
        },

        'Source' : {
            'checker'  : 'sparta.checkers.SpartaSourceChecker',
            'solver'   : 'sparta.checkers.SpartaSourceSolver',
            'stubs'    : path.realpath(path.join(checkers_base, 'sparta', 'checkers', 'information_flow.astub'))
        },

        'Trusted' : {
            'checker'  : 'trusted.TrustedChecker'
        }
    }

    parser = argparse.ArgumentParser(description=description(), epilog=epilog())
    parser.add_argument("checker_to_run", help='Checker specifies which of the built in checkers you would like to use.  ', choices=checker_to_settings.keys())

    add_parser_args(parser, False)
    args = parser.parse_args()

    checker_settings = checker_to_settings[args.checker_to_run]

    if checker_settings == checker_to_settings['Sink'] or checker_settings == checker_to_settings['Source']:
        if args.json_file is not None:
            error("Sparta checkers do not have serializable constraints.  -json-file is an invalid option\n")



    for key in checker_settings:
        if getattr(args, key) is None:
            setattr(args, key, checker_settings[key])

    inference.main(args)

if __name__=='__main__':
    main()