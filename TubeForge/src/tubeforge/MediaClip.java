package tubeforge;

import java.nio.file.Path;

public final class MediaClip {
    private final Path path;
    private final double duration;
    private double inPoint;
    private double outPoint;

    public MediaClip(Path path, double duration) {
        this(path, duration, 0, duration);
    }

    public MediaClip(Path path, double duration, double inPoint, double outPoint) {
        this.path = path;
        this.duration = duration;
        this.inPoint = Math.max(0, inPoint);
        this.outPoint = Math.min(duration, outPoint);
    }

    public Path path() { return path; }
    public double duration() { return duration; }
    public double inPoint() { return inPoint; }
    public double outPoint() { return outPoint; }
    public double editedDuration() { return Math.max(0, outPoint - inPoint); }

    public void setTrim(double in, double out) {
        if (in < 0 || out > duration || in >= out) {
            throw new IllegalArgumentException("The out point must be after the in point.");
        }
        inPoint = in;
        outPoint = out;
    }

    @Override public String toString() {
        return path.getFileName() + "   " + time(editedDuration());
    }

    public static String time(double seconds) {
        int total = (int) Math.round(seconds);
        return "%02d:%02d:%02d".formatted(total / 3600, (total % 3600) / 60, total % 60);
    }
}
