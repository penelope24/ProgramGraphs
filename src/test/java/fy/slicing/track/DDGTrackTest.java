package fy.slicing.track;

import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import fy.TestUtils;
import fy.progex.build.IPDGBuilder;
import fy.progex.graphs.IPDG;
import fy.slicing.result.CDGTrackResult;
import fy.slicing.result.DDGTrackResult;
import ghaffarian.progex.graphs.pdg.DDEdge;
import ghaffarian.progex.graphs.pdg.PDNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DDGTrackTest {
    String base = "/Users/fy/Documents/data/slicing_cases/cases/icfg2";
    String output = "/Users/fy/Documents/data/slicing_cases/output";

    @Test
    void generateDot() throws IOException {
        List<String> paths = TestUtils.getPaths(base);
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File(base)));
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        IPDG ipdg = IPDGBuilder.buildForAll(paths.toArray(new String[0]), symbolSolver);
    }

    @Test
    void run() throws IOException {
        List<String> paths = TestUtils.getPaths(base);
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(new File(base)));
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        IPDG ipdg = IPDGBuilder.buildForAll(paths.toArray(new String[0]), symbolSolver);
        //run
        String javaPath = "/Users/fy/Documents/data/slicing_cases/cases/icfg2/Class1.java";

    }
}