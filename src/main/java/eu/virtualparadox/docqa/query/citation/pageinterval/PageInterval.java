package eu.virtualparadox.docqa.query.citation.pageinterval;

public record PageInterval(int fromPage, int toPage) {

    public String asString() {
        if (fromPage == toPage) {
            return String.valueOf(fromPage);
        } else {
            return fromPage + "-" + toPage;
        }
    }
}
