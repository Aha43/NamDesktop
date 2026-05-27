package namdesktop.lens;

import namdesktop.model.NamNode;

import java.util.List;

public record BlockedGroup(NamNode blocker, List<NamNode> blocked) {}
