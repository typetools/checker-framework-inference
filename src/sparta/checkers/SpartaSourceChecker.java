package sparta.checkers;


import org.checkerframework.framework.qual.TypeQualifiers;

import sparta.checkers.quals.Source;
import checkers.inference.BaseInferrableChecker;

/**
 * Checker for inferring @Source annotations for SPARTA.
 *
 * Only standard subtyping rules are needed so no methods are overridden.
 */
@TypeQualifiers({ Source.class })
public class SpartaSourceChecker extends BaseInferrableChecker {

}