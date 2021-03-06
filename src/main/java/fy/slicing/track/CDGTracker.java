package fy.slicing.track;

import fy.progex.parse.PDGInfo;
import fy.slicing.result.CDGTrackResult;
import fy.slicing.result.DDGTrackResult;
import fy.slicing.solver.cdg.ControlBindNodeSolver;
import ghaffarian.progex.graphs.pdg.DDEdge;
import ghaffarian.progex.graphs.pdg.PDNode;

import java.util.Set;
import java.util.stream.Collectors;

public class CDGTracker {

    public static CDGTrackResult<PDNode> track (PDGInfo pdgInfo, DDGTrackResult<PDNode, DDEdge> ddgTrackResult) {
        CDGTrackResult<PDNode> result = new CDGTrackResult<>();
        Set<PDNode> dataBindingNodes = ddgTrackResult.getResDataNodes().stream()
                .map(pdgInfo::findCDNode)
                .collect(Collectors.toSet());
        for (PDNode node : dataBindingNodes) {
            ControlBindNodeSolver solver = new ControlBindNodeSolver(pdgInfo.cdg, node, 3);
            solver.track();
            result.addControlBindingNodes(solver.getControlBindingNodes());
        }
        return result;
    }
}
