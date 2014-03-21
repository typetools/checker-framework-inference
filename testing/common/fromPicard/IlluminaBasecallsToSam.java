package fromPicard;

enum IlluminaDataType {
 	Position, BaseCalls, QualityScores, RawIntensities, Noise, PF, Barcodes;
}

public class IlluminaBasecallsToSam {

    public static final IlluminaDataType[] DATA_TYPES_NO_BARCODE = {
            IlluminaDataType.BaseCalls, 
            IlluminaDataType.QualityScores, 
            IlluminaDataType.Position, 
            IlluminaDataType.PF
    };
}