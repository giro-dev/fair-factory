package dev.agiro.fanel.assistant.domain.recipeimport;

import java.util.List;
import java.util.UUID;

public record ImportSessionInfo(UUID id, String source, List<CandidateInfo> candidates,
                                boolean aiAvailable) {
}
