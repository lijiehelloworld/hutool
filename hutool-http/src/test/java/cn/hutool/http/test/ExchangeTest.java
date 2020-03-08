package cn.hutool.http.test;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.*;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ExchangeTest {


    @Test
//    登陆
    public void login2() throws Exception {
//        String result = HttpUtil.createPost("api.uhaozu.com/goods/description/1120448506").charset(CharsetUtil.UTF_8).execute().body();
//        Console.log(result);
        NumberFormat nf = NumberFormat.getNumberInstance();
        // 保留两位小数
        nf.setMaximumFractionDigits(4);
        // 如果不需要四舍五入，可以使用RoundingMode.DOWN
//			nf.setRoundingMode(RoundingMode.UP);
        System.out.println(RandomUtil.getRandom().nextDouble());
        System.out.println(nf.format(Math.random()));
//        login();
        getRates("BTC", "USDT");
    }

    //获取随机数
    public double getRandomNum(int digits) throws Exception {
        NumberFormat nf = NumberFormat.getNumberInstance();
        // 保留两位小数
        nf.setMaximumFractionDigits(digits);
        // 如果不需要四舍五入，可以使用RoundingMode.DOWN
        nf.setRoundingMode(RoundingMode.UP);
        return Double.valueOf(nf.format(Math.random()));
    }

    //获取费率
    public Double getRates(String coin, String legalCoin) throws InterruptedException {
        try {

            String result = HttpUtil
                    .get("https://www.bimin.co/api/quote/v1/rates?tokens=" + coin +
                            "&legalCoins=" + legalCoin);
            JSONObject parse = (JSONObject) JSONUtil.parse(result);
            Double aDouble = parse.getJSONArray("data").getJSONObject(0).getJSONObject("rates").getDouble(legalCoin);
            return aDouble;
        } catch (Exception e) {
            e.printStackTrace();
            return 0.00;
        }
    }

    //获取token币种列表
    public List<String> getTokenList() throws InterruptedException {
        // 某些接口对Accept头有特殊要求，此处自定义头
        String result = HttpUtil
                .createPost("http://36.153.147.94:81/market/symbol-thumb-trend")
                .header(Header.ACCEPT, "*/*")
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
//                .header("x-auth-token", "31ad8e93-a6ef-4aa4-953b-2f767a97e32f")
//                .form(form)
                .execute()
                .body();
//        Console.log(result);
        JSONArray objects = JSONUtil.parseArray(result);
        ArrayList<String> list = new ArrayList<>();
        objects.stream().forEach(object -> list.add(JSONUtil.parseObj(object).getStr("symbol")));
        System.out.println(list.toString());
        return list;
//        Console.log("name:{}-token:{}", name, token);
//        return token;
    }

    //获取token
    public String login(String name, String pwd) throws InterruptedException {
        // 某些接口对Accept头有特殊要求，此处自定义头
        HashMap<String, Object> form = new HashMap<>();
        form.put("username", name);
        form.put("password", pwd);
        String result = HttpUtil
                .createPost("http://36.153.147.94:81/uc/login")
                .header(Header.ACCEPT, "*/*")
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header("x-auth-token", "31ad8e93-a6ef-4aa4-953b-2f767a97e32f")
                .form(form)
                .execute()
                .body();
//        Console.log(result);
        JSONObject parse = (JSONObject) JSONUtil.parse(result);
        String token = parse.getJSONObject("data").getStr("token");
        Console.log("name:{}-token:{}", name, token);
        return token;
    }

    @Test
    public void testOrder() throws Exception {
//        交易对
        List<String> tokenList = getTokenList();
        String token1 = login("17709694505", "Lijie1014");
//        String token2 = login("17709694505", "Lijie1014");
//        String token2 = login("13186867407", "1234Qwer");
//        ExecutorService cachedThreadPool = Executors.newFixedThreadPool(20);
        ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

        for (String token : tokenList) {
            Thread.sleep(Double.valueOf(1000 * (1 + getRandomNum(4))).longValue());
            String[] split = token.split("/");
            String coin = split[0];
            String legalCoin = split[1];//
//                            String coin ="BTC";
//                            String legalCoin = "USDT";
            try {
                cachedThreadPool.execute(
                        new Runnable() {
                            @SneakyThrows
                            @Override
                            public void run() {
                                sellOreder(coin, legalCoin, token1, 200000);
                            }
                        });
                cachedThreadPool.execute(
                        new Runnable() {
                            @SneakyThrows
                            @Override
                            public void run() {
                                buyOreder(coin, legalCoin, token1, 200000);
                            }
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        do {
            Thread.sleep(6000);
//            System.out.println(cachedThreadPool.isTerminated());
        } while (!cachedThreadPool.isTerminated());
    }


    //    卖单
    public void sellOreder(String coin, String legalCoin, String token, Integer num) throws Exception {
        // 某些接口对Accept头有特殊要求，此处自定义头
//        String token = login("17709694505", "Lijie1014");
//        String token = login("13186867407", "1234Qwer");
        //错误尝试次数
        Integer errNum = 0;
//        获取汇率
        Double rates = getRates(coin, legalCoin);
        for (int i = 0; i < num; i++) {
            //每20秒更新一次价格
            if (System.currentTimeMillis() / 1000 % 10 == 0) {
                rates = getRates(coin, legalCoin);
            }
            //5次失败结束所有循环
            if (errNum > 5) {
                break;
            }
            //获取价格失败跳出此次循环
            if (rates == null || rates == 0.00) {
                errNum++;
                Thread.sleep(1000 * errNum * errNum);
                continue;
            }
            //重新置为0
            errNum = 0;
            HashMap<String, Object> form = new HashMap<>();
            form.put("symbol", coin + "/" + legalCoin);
            form.put("price", rates * (0.98 + 0.04 * getRandomNum(4)));
            form.put("amount", 100 / rates * (0.1 + getRandomNum(3)));
//            form.put("amount", 0.000000000001);
            form.put("direction", "SELL");
            form.put("type", "LIMIT_PRICE");
            Console.log(form.toString());
            String result = HttpUtil
                    .createPost("http://36.153.147.94:81/exchange/order/add")
                    .header(Header.ACCEPT, "*/*")
                    .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .header("x-auth-token", token)
                    .form(form)
                    .execute()
                    .body();
            Console.log(result);
            try {
                JSONObject jsonObject = JSONUtil.parseObj(result);
                if (jsonObject.getStr("message").equals("不支持的币种!")) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(Double.valueOf(1000 * (1 + getRandomNum(4))).longValue());

        }
    }

    //    mai单2
    public void buyOreder(String coin, String legalCoin, String token, Integer num) throws Exception {
        // 某些接口对Accept头有特殊要求，此处自定义头
//        String token = login("13186867407", "1234Qwer");
//        String token = login("17709694505", "Lijie1014");
        Double rates = getRates(coin, legalCoin);
        //错误尝试次数
        Integer errNum = 0;
        if (rates == null || rates == 0.00) {
            return;
        }
        for (int i = 0; i < num; i++) {
            //每20秒更新一次价格
            if (System.currentTimeMillis() / 1000 % 10 == 0) {
                rates = getRates(coin, legalCoin);
            }
            //5次失败结束所有循环
            if (errNum > 5) {
                break;
            }
            //获取价格失败跳出此次循环
            if (rates == null || rates == 0.00) {
                errNum++;
                Thread.sleep(1000 * errNum * errNum);
                continue;
            }
            //重新置为0
            errNum = 0;
            HashMap<String, Object> form = new HashMap<>();
            form.put("symbol", coin + "/" + legalCoin);
            form.put("price", rates * (0.98 + 0.04 * getRandomNum(4)));
            form.put("amount", 100 / rates * (0.1 + getRandomNum(3)));
            form.put("direction", "BUY");
            form.put("type", "LIMIT_PRICE");
            Console.log(form.toString());
            String result = HttpUtil
                    .createPost("http://36.153.147.94:81/exchange/order/add")
                    .header(Header.ACCEPT, "*/*")
                    .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .header("x-auth-token", token)
                    .form(form)
                    .execute()
                    .body();
            Console.log(result);
            try {
                JSONObject jsonObject = JSONUtil.parseObj(result);
                if (jsonObject.getStr("message").equals("不支持的币种!")) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Thread.sleep(Double.valueOf(1000 * (1 + getRandomNum(4))).longValue());
        }
    }

    @Test
    public void getOreder() throws Exception {
        // 某些接口对Accept头有特殊要求，此处自定义头
//        String token = login("13186867407", "1234Qwer");
        String token = login("17709694505", "Lijie1014");
        //订单数
        Integer num = 0;
        //线程数
        Integer threadNum = 5;
        do {
            HashMap<String, Object> form = new HashMap<>();
            form.put("pageNo", 1);
            form.put("pageSize", 1000);
            int activeCount=0;
//            Console.log(form.toString());
            String result = HttpUtil
                    .createPost("http://36.153.147.94:81/exchange/order/personal/current")
                    .header(Header.ACCEPT, "*/*")
                    .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .header("x-auth-token", token)
                    .form(form)
                    .execute()
                    .body();
//            Console.log(result);
            ExecutorService cachedThreadPool = Executors.newFixedThreadPool(threadNum);
            try {
                JSONArray content = JSONUtil.parseObj(result).getJSONArray("content");
                num = content.size();
                for (int i = 0; i < threadNum; i++) {
                    Integer finalNum = num;
                    int finalI = i;
                    cachedThreadPool.execute(new Runnable() {
                        @SneakyThrows
                        @Override
                        public void run() {
                            for (int j = finalI * (finalNum / threadNum); j < (finalI + 1) * (finalNum / threadNum); j++) {
                                String orderId = JSONUtil.parseObj(content.get(j)).getStr("orderId");
                                cancalOreder(token, orderId);
//                                Console.log("当前线程名：{}----j:{}     -----------     (k + 1) * (finalNum / threadNum):{}" ,Thread.currentThread().getId()+Thread.currentThread().getName(),j,(finalI + 1) * (finalNum / threadNum));
                                Thread.sleep(Double.valueOf(100 * (1 + getRandomNum(4))).longValue());
                            }
                        }
                    });

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            do {
                Thread.sleep(6000);
                activeCount = ((ThreadPoolExecutor) cachedThreadPool).getActiveCount();
                System.out.println(activeCount);
            } while (activeCount>0);
        } while (num != 0);
    }

    public void cancalOreder(String token, String orderId) throws Exception {
        // 某些接口对Accept头有特殊要求，此处自定义头
//        String token = login("13186867407", "1234Qwer");
        Integer integer = 0;
        String result = HttpUtil
                .createPost("http://36.153.147.94:81/exchange/order/cancel/" + orderId)
                .header(Header.ACCEPT, "*/*")
                .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header("x-auth-token", token)
                .execute()
                .body();
        Console.log(result);
    }
}
