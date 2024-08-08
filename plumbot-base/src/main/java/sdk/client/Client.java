package sdk.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import me.regadpole.plumbot.PlumBot;
import sdk.anno.Param;
import sdk.config.CQConfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户端父类 集成公用方法
 */
public class Client {

//    private static Log log = LogFactory.get();

    protected CQConfig config;

    public Client(CQConfig config) {
        this.config = config;
    }

    /**
     * 获取url
     *
     * @return
     */
    protected String getUrl() {
        StackTraceElement stacks = (new Throwable()).getStackTrace()[1];
        String methodName = stacks.getMethodName();
        return getUrl(this.upperCharToUnderLine(methodName));
    }


    /**
     * 获取url
     *
     * @return
     */
    protected String getUrl(String uri) {
        StringBuilder builder = new StringBuilder();
        builder.append(config.getUrl());
        builder.append("/");
        builder.append(uri);
        if (config.getIsAccessToken()) {
            //使用token鉴权
            builder.append("?access_token=");
            builder.append(config.getToken());
        }
        return builder.toString();
    }

    /**
     * 创建基础的post请求
     *
     * @param url
     * @return
     */
    public static HttpRequest createPostRequest(String url) {
        return HttpRequest.post(url);
    }

    /**
     * 创建基础的get请求
     *
     * @param url
     * @return
     */
    public static HttpRequest createGetRequest(String url) {
        return HttpRequest.get(url);
    }

    /**
     * 根据调用方的@Param进行封装参数 并构建get请求
     *
     * @param url
     * @param params 需要写入实际类型，例如ArrayList不能写List（调用方）
     * @return
     */
    public static HttpRequest createGetRequest(String url, Object... params) {
        try {
            StackTraceElement traceElement = getCaller();//获取调用的类
            Map<String, Object> paramMap = createParamMap(traceElement, params);

            HttpRequest get = HttpRequest.get(url);
            get.form(paramMap);
            return get;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据调用方的@Param进行封装参数 并构建post请求
     *
     * @param url
     * @param params
     * @return
     */
    public static HttpRequest createPostRequest(String url, Object... params) {
        try {
            StackTraceElement traceElement = getCaller();//获取调用的类
            Map<String, Object> paramMap = createParamMap(traceElement, params);

            HttpRequest post = HttpRequest.post(url);
            post.form(paramMap);
            return post;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 构建请求参数
     *
     * @param traceElement
     * @param params
     * @return
     */
    private static Map<String, Object> createParamMap(StackTraceElement traceElement, Object... params) {
        try {
            Class<?> aClass = Class.forName(traceElement.getClassName());
            Map<String, Object> paramMap = new HashMap<>();

            Class[] classes = new Class[params.length];
            for (int i = 0; i < params.length; i++) {
                classes[i] = params[i].getClass();//获取参数类型
            }
            Method method = aClass.getMethod(traceElement.getMethodName(), classes);//获取对应方法
            Annotation[][] annotations = method.getParameterAnnotations();
            for (int i = 0; i < annotations.length; i++) {
                paramMap.put(((Param) annotations[i][0]).value(), params[i]);//封装请求参数
            }
            return paramMap;
        } catch (Exception e) {
//            log.error(e);
            PlumBot.INSTANCE.getSLF4JLogger().error(Arrays.toString(e.getStackTrace()));
        }
        return null;
    }

    /**
     * 将data反序列化为list类型的数据
     *
     * @param body
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> List<T> toListByData(String body, Class<T> tClass) {
        JSONObject obj = JSONUtil.parseObj(body);
        JSONArray array = obj.getJSONArray("data");
        return toList(array, tClass);
    }

    /**
     * 将数据反序列化为list类型的数据
     *
     * @param array
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> List<T> toList(JSONArray array, Class<T> tClass) {
        if (array == null) {
            return null;
        }
        return JSONUtil.toList(array, tClass);
    }

    /**
     * 将data反序列化为泛型对应类型的数据
     *
     * @param body
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> T toBeanByData(String body, Class<T> tClass) {
        JSONObject obj = JSONUtil.parseObj(body);
        JSONObject data = obj.getJSONObject("data");
        return toBean(data, tClass);
    }

    /**
     * 将数据反序列化为泛型对应类型的数据
     *
     * @param object
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> T toBean(JSONObject object, Class<T> tClass) {
        if (object == null || "".equals(object)) {
            return null;
        }
        return JSONUtil.toBean(object, tClass);
    }

    /**
     * 获取调用方
     *
     * @return
     */
    private static StackTraceElement getCaller() {
        StackTraceElement stacks = (new Throwable()).getStackTrace()[2];
        return stacks;
    }

    /**
     * 获取data下面的某个值，并反序列化为对象
     *
     * @param name
     * @param json
     * @param tClass
     * @return
     */
    public static <T> T getDataValue(String name, String json, Class<T> tClass) {
        JSONObject object = JSONUtil.parseObj(json);
        if (object == null || "".equals(object)) {
            return null;
        }
        return (T) object.getJSONObject("data").get(name);
    }

    /**
     * 获取data下面的某个值，并反序列化为list
     *
     * @param name
     * @param json
     * @param tClass
     * @return
     */
    public static <T> List<T> getListValue(String name, String json, Class<T> tClass) {
        JSONObject object = JSONUtil.parseObj(json);
        if (object == null || "".equals(object)) {
            return null;
        }
        JSONArray jsonArray = object.getJSONObject("data").getJSONArray(name);
        return JSONUtil.toList(jsonArray, tClass);
    }

    /**
     * 将大写字符串改为下划线+小写
     *
     * @param param 例如: sendPrivateMessage
     * @return 返回： send_private_message
     */
    public static String upperCharToUnderLine(String param) {
        Pattern p = Pattern.compile("[A-Z]");
        if (param == null || param.equals("")) {
            return "";
        }
        StringBuilder builder = new StringBuilder(param);
        Matcher mc = p.matcher(param);
        int i = 0;
        while (mc.find()) {
            builder.replace(mc.start() + i, mc.end() + i, "_" + mc.group().toLowerCase());
            i++;
        }

        if ('_' == builder.charAt(0)) {
            builder.deleteCharAt(0);
        }
        return builder.toString();
    }
}
