package sdk.client.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * ocr响应
 */
public class OcrImage {
    private List<TextDetection> texts;
    private String language;

    public List<TextDetection> getTexts() {
        return texts;
    }

    public void setTexts(List<TextDetection> texts) {
        this.texts = texts;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return "OcrImage{" +
                "texts=" + texts +
                ", language='" + language + '\'' +
                '}';
    }

    public class TextDetection {
        private String text;//	文本
        private Integer confidence;//	置信度
        private List<Vector2> coordinates;//	坐标

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Integer getConfidence() {
            return confidence;
        }

        public void setConfidence(Integer confidence) {
            this.confidence = confidence;
        }

        public List<Vector2> getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(List<Vector2> coordinates) {
            this.coordinates = coordinates;
        }

        @Override
        public String toString() {
            return "TextDetection{" +
                    "text='" + text + '\'' +
                    ", confidence=" + confidence +
                    ", coordinates='" + coordinates + '\'' +
                    '}';
        }
    }

    public class Vector2{
        private BigDecimal x;
        private BigDecimal y;

        public BigDecimal getX() {
            return x;
        }

        public void setX(BigDecimal x) {
            this.x = x;
        }

        public BigDecimal getY() {
            return y;
        }

        public void setY(BigDecimal y) {
            this.y = y;
        }

        @Override
        public String toString() {
            return "Vector2{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }
}
