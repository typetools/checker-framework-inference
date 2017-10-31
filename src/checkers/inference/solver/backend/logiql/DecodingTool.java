package checkers.inference.solver.backend.logiql;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;

import checkers.inference.solver.frontend.Lattice;
import checkers.inference.solver.util.NameUtils;

/**
 * DecodingTool decodes the result from LogicBlox, change the form to human
 * readable form and put the result to HashMap result and return it to
 * LogicSolver.
 *
 * @author Jianchu Li
 *
 */
public class DecodingTool {

    private final Map<Integer, AnnotationMirror> result = new HashMap<Integer, AnnotationMirror>();
    private final String path;
    private final Set<Integer> varSlotIds;
    private final Lattice lattice;
    private final int nth;

    public DecodingTool(Set<Integer> varSlotIds, String path, Lattice lattice, int nth) {
        this.varSlotIds = varSlotIds;
        this.path = path;
        this.lattice = lattice;
        this.nth = nth;
    }

    public Map<Integer, AnnotationMirror> decodeResult() {
        setDefault();
        try {
            decodeLogicBloxOutput();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * DecodeLogicBloxOutput decodes the LogicBloxOutput, and put it in HashMap
     * result.
     *
     * @throws FileNotFoundException
     */
    private void decodeLogicBloxOutput() throws FileNotFoundException {
        Map<String, AnnotationMirror> nameMap = mapStringToAnnoMirror();
        String readPath = path + "/logicbloxOutput" + nth + ".txt";
        InputStream in = new FileInputStream(readPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                String[] s = line.replaceAll("\"", "").split(" ");
                int slotID = Integer.parseInt(s[0]);
                AnnotationMirror annotation = nameMap.get(s[s.length - 1]);
                result.put(slotID, annotation);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setDefault() {
        for (Integer varSlotId : varSlotIds) {
            result.put(varSlotId, lattice.top);
        }
    }

    private Map<String, AnnotationMirror> mapStringToAnnoMirror() {
        Map<String, AnnotationMirror> nameMap = new HashMap<String, AnnotationMirror>();
        for (AnnotationMirror annoMirror : lattice.allTypes) {
            String simpleName = NameUtils.getSimpleName(annoMirror);
            nameMap.put(simpleName, annoMirror);
        }
        return nameMap;
    }
}
