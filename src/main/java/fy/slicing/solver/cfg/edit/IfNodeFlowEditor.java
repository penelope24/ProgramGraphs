package fy.slicing.solver.cfg.edit;

import fy.progex.parse.PDGInfo;
import fy.slicing.result.CFGTrackResult;
import fy.slicing.solver.cdg.ChildNodeSolver;
import ghaffarian.graphs.Edge;
import ghaffarian.progex.NodeType;
import ghaffarian.progex.graphs.cfg.CFEdge;
import ghaffarian.progex.graphs.cfg.CFNode;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.graphs.pdg.CDEdge;
import ghaffarian.progex.graphs.pdg.ControlDependenceGraph;
import ghaffarian.progex.graphs.pdg.DDEdge;
import ghaffarian.progex.graphs.pdg.PDNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  if节点的控制流共有三种情况：
 *  1. 基本分支
 *      false分支指向endif，但如果true/false分支均以terminal结束，则连不到endif
 *  2. if级联
 *      a. 顺序级联
 *      b. follow-up级联
 *      无论是哪种级联，false分支均直接指向endif
 *  3. if嵌套
 *      最复杂的情况，嵌套可能发生在任一分支，因此不能确定从哪个分支去追溯endif
 *  会有一种极端情况，两个分支都以return结尾，因此都连不到endif上。
 *  这个时候以return结尾，保持endif不连接
 */

public class IfNodeFlowEditor extends FlowEditor {

    private enum Type {
        basic,
        follow_up,
        nested
    }
    // provide
    CFNode ifNode;
    PDNode ifPDNode;
    ControlFlowGraph graph;

    // parse
    Type ifType;
    CFNode endNode;
    Edge<CFNode, CFEdge> brTrue;
    Edge<CFNode, CFEdge> brFalse;
    List<CFNode> validChildrenTrue = new ArrayList<>();
    List<CFNode> validChildrenFalse = new ArrayList<>();

    public IfNodeFlowEditor(PDGInfo pdgInfo, Set<CFNode> skeletonNodes, CFGTrackResult<PDNode,DDEdge,CFNode,CFEdge> cfgResult, CFNode ifNode) {
        super(pdgInfo, skeletonNodes, cfgResult);
        this.ifNode = ifNode;
        this.ifPDNode = pdgInfo.findCDNode(ifNode);
        this.graph = pdgInfo.cfg;
    }

    public void parse() {
        this.ifType = analyze_if_type();
        brTrue = graph.copyOutgoingEdges(ifNode).stream()
                .filter(edge -> edge.label.type == CFEdge.Type.TRUE)
                .findFirst().orElse(null);
        assert brTrue != null;
        brFalse = graph.copyOutgoingEdges(ifNode).stream()
                .filter(edge -> edge.label.type == CFEdge.Type.FALSE)
                .findFirst().orElse(null);
        assert brFalse != null;
        // 寻找endif
        Deque<Edge<CFNode, CFEdge>> visiting = new ArrayDeque<>();
        switch (ifType) {
            case basic:
                visiting.add(brFalse);
                visiting.add(brTrue);
                while (!visiting.isEmpty()) {
                    Edge<CFNode, CFEdge> edge = visiting.pop();
                    CFNode node = edge.target;
                    if (node.getCode().equals("endif")) {
                        endNode = node;
                        break;
                    }
                    visiting.addAll(graph.copyOutgoingEdges(node));
                }
            case follow_up:
                visiting.add(brFalse);
                while (!visiting.isEmpty()) {
                    Edge<CFNode, CFEdge> edge = visiting.pop();
                    CFNode node = edge.target;
                    if (node.getCode().equals("endif")) {
                        endNode = node;
                        break;
                    }
                    visiting.addAll(graph.copyOutgoingEdges(node));
                }
                break;
            case nested:
                Deque<Edge<CFNode, CFEdge>> reverse = new ArrayDeque<>();
                Edge<CFNode, CFEdge> dummy = new Edge<>(null, null, ifNode);
                visiting.add(dummy);
                int count = 0;
                CFNode first_endif = null;
                while (!visiting.isEmpty()) {
                    Edge<CFNode, CFEdge> edge = visiting.pop();
                    CFNode node = edge.target;
                    if (edge.source != null) {
                        if (edge.label.type == CFEdge.Type.TRUE
                                || edge.label.type == CFEdge.Type.FALSE) {
                            count++;
                        }
                    }
                    if (node.getCode().equals("endif")) {
                        first_endif = node;
                        break;
                    }
                    reverse.addAll(graph.copyOutgoingEdges(node));
                    while (!reverse.isEmpty())
                        visiting.push(reverse.pop());
                }
                assert first_endif != null;
                while (count-1 > 0) {
                    first_endif = graph.copyOutgoingEdges(first_endif).stream()
                            .map(edge -> edge.target)
                            .findFirst().orElse(null);
                    count--;
                }
                endNode = first_endif;
                break;
        }
        List<PDNode> validPDChildrenTrue = findValidChildren(ifPDNode, CDEdge.Type.TRUE);
        validChildrenTrue = validPDChildrenTrue.stream()
                .map(pdgInfo::findCFNodeByCDNode)
                .collect(Collectors.toList());
        List<PDNode> validPDChildrenFalse = findValidChildren(ifPDNode, CDEdge.Type.FALSE);
        validChildrenFalse = validPDChildrenFalse.stream()
                .map(pdgInfo::findCFNodeByCDNode)
                .collect(Collectors.toList());
        // 有极少数情况（如双分支return）连不到endif，因此不能assert
        ifNode.setProperty("endnode", endNode);
    }

    /**
     * 判断if结构的种类
     */
    private Type analyze_if_type() {
        ControlDependenceGraph cdg = pdgInfo.cdg;
        ChildNodeSolver solver = new ChildNodeSolver(cdg);
        List<PDNode> children = solver.find_first_level_children(ifPDNode);
        boolean has_inner_if = children.stream()
                .anyMatch(node -> node.getType() == NodeType.IF);
        if (ifPDNode.getProperty("cascade") != null
                || ifPDNode.getProperty("cascade_start") != null) {
            return Type.follow_up;
        }
        else if (!has_inner_if) {
            return Type.basic;
        }
        else {
            return Type.nested;
        }
    }

    public void edit(){
        // true
        analyzeBranch(ifNode, validChildrenTrue, CFEdge.Type.TRUE, endNode);
        // false
        if (!validChildrenFalse.isEmpty()) {
            analyzeBranch(ifNode, validChildrenFalse, CFEdge.Type.FALSE, endNode);
        }
        else {
            addEdge(ifNode, endNode, CFEdge.Type.FALSE);
        }

    }
    @Override
    public void analyzeBranch(CFNode brNode, List<CFNode> childNodes, CFEdge.Type type, CFNode endNode) {
        childNodes.removeIf(cfNode -> !is_valid(cfNode));
        int size = childNodes.size();
        if (size == 0) {
            // do nothing
        }
        else if (size == 1) {
            CFNode child = childNodes.get(0);
            // if级联情形
            if (type == CFEdge.Type.FALSE && brFalse.target == endNode) {
                addEdge(brNode, endNode, CFEdge.Type.FALSE);
                addEdge(endNode, child, CFEdge.Type.EPSILON);
            }
            else {
                addEdge(brNode, child, type);
                handle_last_child(child);
            }

        }
        else {
            // if级联情形
            if (type == CFEdge.Type.FALSE && brFalse.target == endNode) {
                addEdge(brNode, endNode, CFEdge.Type.FALSE);
                addEdge(endNode, childNodes.get(0), CFEdge.Type.EPSILON);
            }
            else {
                addEdge(brNode, childNodes.get(0), type);
            }
            for (int i=0; i<size-1; i++) {
                CFNode src = childNodes.get(i);
                CFNode tgt = childNodes.get(i+1);
                CFNode src_end = transfer(src);
                if (src_end != null) {
                    addEdge(transfer(src), tgt, CFEdge.Type.EPSILON);
                }
            }
            handle_last_child(childNodes.get(childNodes.size()-1));
        }
    }

    private void handle_last_child(CFNode lastChild) {
        if (!lastChild.isTerminal() && ifType != Type.follow_up) {
            CFNode child_end = transfer(lastChild);
            if (child_end != null) {
                addEdge(child_end, endNode, CFEdge.Type.EPSILON);
            }
        }
        else {
            switch (lastChild.getType()) {
                case THROW:
                case RETURN:
                    break;
                case BREAK: {
                    CFNode next = pdgInfo.cfg.copyOutgoingEdges(lastChild).stream()
                            .map(edge -> edge.target)
                            .findFirst().orElse(null);
                    assert next != null;
                    assert next.getType() == NodeType.HELP;
                    addEdge(lastChild, next, CFEdge.Type.EPSILON);
                    break;
                }
                case CONTINUE: {
                    CFNode next = pdgInfo.cfg.copyOutgoingEdges(lastChild).stream()
                            .map(edge -> edge.target)
                            .findFirst().orElse(null);
                    assert next != null;
                    assert next.isBranch();
                    addEdge(lastChild, next, CFEdge.Type.EPSILON);
                }
            }
        }
    }



}
