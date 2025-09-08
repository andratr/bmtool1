package org.learningjava.bmtool1.application.port;

import org.learningjava.bmtool1.domain.model.PlsqlSnippet;

import java.util.List;
import java.util.Map;

public interface SnippetRepository {
    void savePending(PlsqlSnippet snippet);
    List<PlsqlSnippet> findPending();
    void saveTranslated(PlsqlSnippet snippet);
    List<PlsqlSnippet> findTranslatedByEventCode(String eventCode);

    // ✅ all translated snippets flat
    List<PlsqlSnippet> findAllTranslated();

    // ✅ grouped by eventCode (or file identifier)
    Map<String, List<PlsqlSnippet>> findAllTranslatedGrouped();
}
