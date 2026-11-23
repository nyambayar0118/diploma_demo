package mn.diploma.routeplanner.auth;

/** Хэрэглэгчийн ашиглахыг зөвшөөрч болох хувийн мэдээллийн төрөл, зорилго, хадгалах хугацааны хамт (ХШ04, ХШ05). */
public enum ConsentDataType {
    INTERESTS("Сонирхолд нийцсэн газар санал болгох", 365),
    LOCATION("Ойролцоох газрыг санал болгох, маршрутын хугацааг тооцох", 30),
    LOCATION_SHARING("Байршлаа найзууддаа харуулах", 7),
    INTERACTION_HISTORY("Санал болголтын загварыг сургаж сайжруулах (машин сургалт)", 365);

    private final String purpose;
    private final int retentionDays;

    ConsentDataType(String purpose, int retentionDays) {
        this.purpose = purpose;
        this.retentionDays = retentionDays;
    }

    public String purpose() {
        return purpose;
    }

    public int retentionDays() {
        return retentionDays;
    }
}
