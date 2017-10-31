package checkers.inference.solver.backend.z3;

import java.math.BigInteger;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;

public interface Z3BitVectorCodec {

    /**
    * Get the fixed Bit Vector size.
    * @return the fixed Bit Vector size
    */
    int getFixedBitVectorSize();

    /**
     * Encode a given AnnotationMirror to a numeric value whose binary representation is the encoded bit vector.
     * @param am a given AnnotationMirror.
     * @return numeral value of the encoded bit vector, -1 if the given {@code am} cannot be encoded.
     */
    BigInteger encodeConstantAM(AnnotationMirror am);

    AnnotationMirror decodeNumeralValue(BigInteger numeralValue, ProcessingEnvironment processingEnvironment);
}
