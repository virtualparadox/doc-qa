package eu.virtualparadox.docqa.query.citation;

import eu.virtualparadox.docqa.query.citation.pageinterval.PageInterval;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Citation {

    public final String docId;
    public final String title;
    public final List<PageInterval> pageIntervals;

    public Citation(final String docId, final String title, final List<PageInterval> pageIntervals) {
        this.docId = docId;
        this.title = title;
        this.pageIntervals = pageIntervals;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Citation citation = (Citation) o;
        return Objects.equals(docId, citation.docId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docId);
    }

    public String asString() {
        return title + (pageIntervals.isEmpty() ? "" : " p. " + getPagesAsString());
    }

    private String getPagesAsString() {
        final List<String> intervalsAsString = new ArrayList<>();
        for (final PageInterval interval : pageIntervals) {
            intervalsAsString.add(interval.asString());
        }
        return StringUtils.join(intervalsAsString, ", ");
    }
}
