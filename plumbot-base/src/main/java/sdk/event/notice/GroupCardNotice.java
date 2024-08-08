package sdk.event.notice;

import sdk.event.global.GroupNotice;

/**
 * 群成员名片更新
 */
public class GroupCardNotice extends GroupNotice {
    private Long cardNew;//新名片
    private Long cardOld;//旧名片

    public Long getCardNew() {
        return cardNew;
    }

    public void setCardNew(Long cardNew) {
        this.cardNew = cardNew;
    }

    public Long getCardOld() {
        return cardOld;
    }

    public void setCardOld(Long cardOld) {
        this.cardOld = cardOld;
    }

    @Override
    public String toString() {
        return "GroupCardNotice{" +
                "cardNew=" + cardNew +
                ", cardOld=" + cardOld +
                "} " + super.toString();
    }
}
