package sdk.type;

/**
 * 提交类型，可能自定义会更好
 */
public enum  SubType {

    POKE("poke"),//戳一戳
    ;

    private String value;

    SubType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
