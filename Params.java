
/** Tham số khai phá w-PFI. */
public final class Params {
    public final int msup; // hỗ trợ tối thiểu (số giao dịch)
    public final double t; // ngưỡng xác suất tối thiểu (tin cậy)
    public final double alpha; // hệ số cắt tỉa xấp xỉ (0 -> tắt, 0.5..0.9 gợi ý)
    public final Mode mode; // EXACT (DP) hoặc APPROX (Poisson/Normal)
    public final ApproxFamily approx; // Poisson hoặc Normal

    public enum Mode {
        EXACT, APPROX
    }

    public enum ApproxFamily {
        POISSON, NORMAL
    }

    public Params(int msup, double t, double alpha, Mode mode, ApproxFamily approx) {
        if (msup <= 0)
            throw new IllegalArgumentException("msup > 0");
        if (t <= 0 || t > 1)
            throw new IllegalArgumentException("t in (0,1]");
        if (alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("alpha in [0,1]");
        this.msup = msup;
        this.t = t;
        this.alpha = alpha;
        this.mode = mode;
        this.approx = approx;
    }
}
