package namdesktop.lens;

import namdesktop.model.NamNode;

import java.util.List;

public record ChildSection(NamNode project, List<NamNode> directActions) {}
