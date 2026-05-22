package namdesktop.lens;

import namdesktop.model.NamNode;

import java.util.List;

public record WorkbenchProjection(
        List<NamNode> breadcrumb,
        List<NamNode> directActions,
        List<ChildSection> childSections) {}
