package com.base.data;

/**
 * @author YISivlay
 */
public class Attendance {

    private final Long id;
    private final String fpCode;
    private final String checkTime;
    private final Boolean isSync;
    private final String status;

    private Attendance(final Builder builder) {
        this.id = builder.id;
        this.fpCode = builder.fpCode;
        this.checkTime = builder.checkTime;
        this.isSync = builder.isSync;
        this.status = builder.status;
    }

    public static class Builder {

        private Long id;
        private String fpCode;
        private String checkTime;
        private Boolean isSync;
        private String status;

        public Attendance build() {
            return new Attendance(this);
        }

        public Builder id(final Long id) {
            this.id = id;
            return this;
        }

        public Builder fpCode(final String fpCode) {
            this.fpCode = fpCode;
            return this;
        }

        public Builder checkTime(final String checkTime) {
            this.checkTime = checkTime;
            return this;
        }

        public Builder isSync(final Boolean isSync) {
            this.isSync = isSync;
            return this;
        }

        public Builder status(final String status) {
            this.status = status;
            return this;
        }
    }

    public String getCheckTime() {
        return checkTime;
    }

    public String getFpCode() {
        return fpCode;
    }

    public Long getId() {
        return id;
    }

    public Boolean getSync() {
        return isSync;
    }

    public String getStatus() {
        return status;
    }
}
