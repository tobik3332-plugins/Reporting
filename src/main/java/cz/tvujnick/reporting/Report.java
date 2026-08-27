package cz.tvujnick.reporting;

public class Report {
    public int id;
    public String reporter;
    public String reported;
    public String reason;
    public String time;
    public boolean open;

    public Report(int id, String reporter, String reported, String reason, String time, boolean open) {
        this.id = id;
        this.reporter = reporter;
        this.reported = reported;
        this.reason = reason;
        this.time = time;
        this.open = open;
    }
}
