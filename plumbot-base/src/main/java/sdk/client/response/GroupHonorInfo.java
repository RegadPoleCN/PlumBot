package sdk.client.response;

import java.util.List;

/**
 * 群荣誉信息
 */
public class GroupHonorInfo {
    private Long group_id;//群号
    private Talkative currentTalkative;//当前龙王, 仅 type 为 talkative 或 all 时有数据
    private List<Member> talkativeList;//历史龙王, 仅 type 为 talkative 或 all 时有数据
    private List<Member> performerList;//群聊之火, 仅 type 为 performer 或 all 时有数据
    private List<Member> legendList;//群聊炽焰, 仅 type 为 legend 或 all 时有数据
    private List<Member> strongNewbieList;//冒尖小春笋, 仅 type 为 strong_newbie 或 all 时有数据
    private List<Member> emotionList;//快乐之源, 仅 type 为 emotion 或 all 时有数据

    public Long getGroup_id() {
        return group_id;
    }

    public void setGroup_id(Long group_id) {
        this.group_id = group_id;
    }

    public Talkative getCurrentTalkative() {
        return currentTalkative;
    }

    public void setCurrentTalkative(Talkative currentTalkative) {
        this.currentTalkative = currentTalkative;
    }

    public List<Member> getTalkativeList() {
        return talkativeList;
    }

    public void setTalkativeList(List<Member> talkativeList) {
        this.talkativeList = talkativeList;
    }

    public List<Member> getPerformerList() {
        return performerList;
    }

    public void setPerformerList(List<Member> performerList) {
        this.performerList = performerList;
    }

    public List<Member> getLegendList() {
        return legendList;
    }

    public void setLegendList(List<Member> legendList) {
        this.legendList = legendList;
    }

    public List<Member> getStrongNewbieList() {
        return strongNewbieList;
    }

    public void setStrongNewbieList(List<Member> strongNewbieList) {
        this.strongNewbieList = strongNewbieList;
    }

    public List<Member> getEmotionList() {
        return emotionList;
    }

    public void setEmotionList(List<Member> emotionList) {
        this.emotionList = emotionList;
    }

    @Override
    public String toString() {
        return "GroupHonorInfo{" +
                "group_id=" + group_id +
                ", currentTalkative=" + currentTalkative +
                ", talkativeList=" + talkativeList +
                ", performerList=" + performerList +
                ", legendList=" + legendList +
                ", strongNewbieList=" + strongNewbieList +
                ", emotionList=" + emotionList +
                '}';
    }

    public class Talkative {
        private Long userId;//QQ 号
        private String nickname;//昵称
        private String avatar;//头像 URL
        private Integer dayCount;//持续天数

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public Integer getDayCount() {
            return dayCount;
        }

        public void setDayCount(Integer dayCount) {
            this.dayCount = dayCount;
        }

        @Override
        public String toString() {
            return "Talkative{" +
                    "userId=" + userId +
                    ", nickname='" + nickname + '\'' +
                    ", avatar='" + avatar + '\'' +
                    ", dayCount=" + dayCount +
                    '}';
        }
    }

    public class Member {
        private Long userId;//QQ 号
        private String nickname;//昵称
        private String avatar;//头像 URL
        private String description;//荣誉描述

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "Member{" +
                    "userId=" + userId +
                    ", nickname='" + nickname + '\'' +
                    ", avatar='" + avatar + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
}
