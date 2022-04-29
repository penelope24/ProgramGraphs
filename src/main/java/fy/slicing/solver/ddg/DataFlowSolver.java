package fy.slicing.solver.ddg;

import fy.slicing.solver.Solver;
import ghaffarian.progex.graphs.pdg.DDEdge;
import ghaffarian.progex.graphs.pdg.DataDependenceGraph;
import ghaffarian.progex.graphs.pdg.PDNode;

public class DataFlowSolver implements Solver<PDNode, DDEdge> {
     public DataDependenceGraph graph;

     public DataFlowSolver(DataDependenceGraph graph) {
          this.graph = graph;
     }
}
