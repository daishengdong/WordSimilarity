package wordsimilarity;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 该类为此项目的主要文件，提供计算词语相似度的一些基本公式，都为静态。
 * 此类线程安全，可以多线程调用。
 * 具体算法参考论文： 《基于＜知网＞的词汇语义相似度计算》论文.pdf
 */
public class WordSimilarity {
    // 词库中所有的具体词，或者义原
    private static Map<String, List<Word>> ALLWORDS = new HashMap<String, List<Word>>();
    /**
     * sim(p1,p2) = alpha/(d+alpha)
     */
    private static double alpha = 1.6;
    /**
     * 计算实词的相似度，参数，基本义原权重
     */
    private static double beta1 = 0.5;
    /**
     * 计算实词的相似度，参数，其他义原权重
     */
    private static double beta2 = 0.2;
    /**
     * 计算实词的相似度，参数，关系义原权重
     */
    private static double beta3 = 0.17;
    /**
     * 计算实词的相似度，参数，关系符号义原权重
     */
    private static double beta4 = 0.13;
    /**
     * 具体词与义原的相似度一律处理为一个比较小的常数. 具体词和具体词的相似度，如果两个词相同，则为1，否则为0.
     */
    private static double gamma = 0.2;
    /**
     * 将任一非空值与空值的相似度定义为一个比较小的常数
     */
    private static double delta = 0.2;
    /**
     * 两个无关义原之间的默认距离
     */
    private static int DEFAULT_PRIMITIVE_DIS = 20;
    /**
     * 知网中的逻辑符号
     */
    private static String LOGICAL_SYMBOL = ",~^";
    /**
     * 知网中的关系符号
     */
    private static String RELATIONAL_SYMBOL = "#%$*+&@?!";
    /**
     * 知网中的特殊符号，虚词，或具体词
     */
    private static String SPECIAL_SYMBOL = "{";
    /**
     * 默认加载文件
     */
    static {
        loadGlossary();
    }

    /**
     * 加载 glossay.dat 文件
     */
    public static void loadGlossary() {
        String line = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("dict/glossary.dat"));
            line = reader.readLine();
            while (line != null) {
                // parse the line
                // the line format is like this:
                // 阿布扎比 N place|地方,capital|国都,ProperName|专,(the United Arab Emirates|阿拉伯联合酋长国)
                line = line.trim().replaceAll("\\s+", " ");
                String[] strs = line.split(" ");
                String word = strs[0];
                String type = strs[1];
                // 因为是按空格划分，最后一部分的加回去
                String related = strs[2];
                for (int i = 3; i < strs.length; i++) {
                    related += (" " + strs[i]);
                }
                // Create a new word
                Word w = new Word();
                w.setWord(word);
                w.setType(type);
                parseDetail(related, w);
                // save this word.
                addWord(w);
                // read the next line
                line = reader.readLine();
            }
        } catch (Exception e) {
            System.out.println("Error line: " + line);
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * 解析具体概念部分，将解析的结果存入<code>Word word</code>.
     * 
     * @param related
     */
    public static void parseDetail(String related, Word word) {
        // spilt by ","
        String[] parts = related.split(",");
        boolean isFirst = true;
        boolean isRelational = false;
        boolean isSimbol = false;
        String chinese = null;
        String relationalPrimitiveKey = null;
        String simbolKey = null;
        for (int i = 0; i < parts.length; i++) {
            // 如果是具体词，则以括号开始和结尾: (Bahrain|巴林)
            if (parts[i].startsWith("(")) {
                parts[i] = parts[i].substring(1, parts[i].length() - 1);
                // parts[i] = parts[i].replaceAll("\\s+", "");
            }
            // 关系义原，之后的都是关系义原
            if (parts[i].contains("=")) {
                isRelational = true;
                // format: content=fact|事情
                String[] strs = parts[i].split("=");
                relationalPrimitiveKey = strs[0];
                String value = strs[1].split("\\|")[1];
                word.addRelationalPrimitive(relationalPrimitiveKey, value);

                continue;
            }
            String[] strs = parts[i].split("\\|");
            // 开始的第一个字符，确定是否为义原，或是其他关系。
            int type = getPrimitiveType(strs[0]);
            // 其中中文部分的词语,部分虚词没有中文解释
            if (strs.length > 1) {
                chinese = strs[1];
            }
            if (chinese != null
                    && (chinese.endsWith(")") || chinese.endsWith("}"))) {
                chinese = chinese.substring(0, chinese.length() - 1);
            }
            // 义原
            if (type == 0) {
                // 之前有一个关系义原
                if (isRelational) {
                    word
                            .addRelationalPrimitive(relationalPrimitiveKey,
                                    chinese);
                    continue;
                }
                // 之前有一个是符号义原
                if (isSimbol) {
                    word.addRelationSimbolPrimitive(simbolKey, chinese);
                    continue;
                }
                if (isFirst) {
                    word.setFirstPrimitive(chinese);
                    isFirst = false;
                    continue;
                } else {
                    word.addOtherPrimitive(chinese);
                    continue;
                }
            }
            // 关系符号表
            if (type == 1) {
                isSimbol = true;
                isRelational = false;
                simbolKey = Character.toString(strs[0].charAt(0));
                word.addRelationSimbolPrimitive(simbolKey, chinese);
                continue;
            }
            if (type == 2) {
                // 虚词
                if (strs[0].startsWith("{")) {
                    // 去掉开始第一个字符 "{"
                    String english = strs[0].substring(1);
                    // 去掉有半部分 "}"
                    if (chinese != null) {
                        word.addStructruralWord(chinese);
                        continue;
                    } else {
                        // 如果没有中文部分，则使用英文词
                        word.addStructruralWord(english);
                        continue;
                    }
                }
            }
        }
    }

    /**
     * <p>
     * 从英文部分确定这个义原的类别。
     * </p>
     * <p>
     * 0-----Primitive<br/> 1-----Relational<br/> 2-----Special
     * </p>
     * 
     * @param english
     * @return 一个代表类别的整数，其值为1，2，3。
     */
    public static int getPrimitiveType(String str) {
        String first = Character.toString(str.charAt(0));
        if (RELATIONAL_SYMBOL.contains(first)) {
            return 1;
        }
        if (SPECIAL_SYMBOL.contains(first)) {
            return 2;
        }
        return 0;
    }

    /**
     * 计算两个词语的相似度
     */
    public static double simWord(String word1, String word2) {
        if (ALLWORDS.containsKey(word1) && ALLWORDS.containsKey(word2)) {
            List<Word> list1 = ALLWORDS.get(word1);
            List<Word> list2 = ALLWORDS.get(word2);
            double max = 0;
            for (Word w1 : list1) {
                for (Word w2 : list2) {
                    double sim = simWord(w1, w2);
                    max = (sim > max) ? sim : max;
                }
            }
            return max;
        }
        System.out.println("其中有词没有被收录");
        return 0.0;
    }

    /**
     * 计算两个词语的相似度
     * @param w1
     * @param w2
     * @return
     */
    public static double simWord(Word w1, Word w2) {
        // 虚词和实词的相似度为零
        if (w1.isStructruralWord() != w2.isStructruralWord()) {
            return 0;
        }
        // 虚词
        if (w1.isStructruralWord() && w2.isStructruralWord()) {
            List<String> list1 = w1.getStructruralWords();
            List<String> list2 = w2.getStructruralWords();
            return simList(list1, list2);
        }
        // 实词
        if (!w1.isStructruralWord() && !w2.isStructruralWord()) {
            // 实词的相似度分为4个部分
            // 基本义原相似度
            String firstPrimitive1 = w1.getFirstPrimitive();
            String firstPrimitive2 = w2.getFirstPrimitive();
            double sim1 = simPrimitive(firstPrimitive1, firstPrimitive2);
            // 其他基本义原相似度
            List<String> list1 = w1.getOtherPrimitives();
            List<String> list2 = w2.getOtherPrimitives();
            double sim2 = simList(list1, list2);
            // 关系义原相似度
            Map<String, List<String>> map1 = w1.getRelationalPrimitives();
            Map<String, List<String>> map2 = w2.getRelationalPrimitives();
            double sim3 = simMap(map1, map2);
            // 关系符号相似度
            map1 = w1.getRelationSimbolPrimitives();
            map2 = w2.getRelationSimbolPrimitives();
            double sim4 = simMap(map1, map2);
            double product = sim1;
            double sum = beta1 * product;
            product *= sim2;
            sum += beta2 * product;
            product *= sim3;
            sum += beta3 * product;
            product *= sim4;
            sum += beta4 * product;
            return sum;
        }
        return 0.0;
    }

    /**
     * map的相似度。
     * 
     * @param map1
     * @param map2
     * @return
     */
    public static double simMap(Map<String, List<String>> map1,
            Map<String, List<String>> map2) {
        if (map1.isEmpty() && map2.isEmpty()) {
            return 1;
        }
        int total =map1.size() + map2.size();
        double sim = 0;
        int count = 0;
        for (String key : map1.keySet()) {
            if (map2.containsKey(key)) {
                List<String> list1 = map1.get(key);
                List<String> list2 = map2.get(key);
                sim += simList(list1, list2);
                count++;
            }
        }
        return (sim + delta * (total-2*count))
                / (total-count);
    }

    /**
     * 比较两个集合的相似度
     * 
     * @param list1
     * @param list2
     * @return
     */
    public static double simList(List<String> list1, List<String> list2) {
        if (list1.isEmpty() && list2.isEmpty())
            return 1;
        int m = list1.size();
        int n = list2.size();
        int big = m > n ? m : n;
        int N = (m < n) ? m : n;
        int count = 0;
        int index1 = 0, index2 = 0;
        double sum = 0;
        double max = 0;
        while (count < N) {
            max = 0;
            for (int i = 0; i < list1.size(); i++) {
                for (int j = 0; j < list2.size(); j++) {
                    double sim = innerSimWord(list1.get(i), list2.get(j));
                    if (sim > max) {
                        index1 = i;
                        index2 = j;
                        max = sim;
                    }
                }
            }
            sum += max;
            list1.remove(index1);
            list2.remove(index2);
            count++;
        }
        return (sum + delta * (big - N)) / big;
    }

    /**
     * 内部比较两个词，可能是为具体词，也可能是义原
     * 
     * @param word1
     * @param word2
     * @return
     */
    private static double innerSimWord(String word1, String word2) {
        boolean isPrimitive1 = Primitive.isPrimitive(word1);
        boolean isPrimitive2 = Primitive.isPrimitive(word2);
        // 两个义原
        if (isPrimitive1 && isPrimitive2)
            return simPrimitive(word1, word2);
        // 具体词
        if (!isPrimitive1 && !isPrimitive2) {
            if (word1.equals(word2))
                return 1;
            else
                return 0;
        }
        // 义原和具体词的相似度, 默认为gamma=0.2
        return gamma;
    }

    /**
     * @param primitive1
     * @param primitive2
     * @return
     */
    public static double simPrimitive(String primitive1, String primitive2) {
        int dis = disPrimitive(primitive1, primitive2);
        return alpha / (dis + alpha);
    }

    /**
     * 计算两个义原之间的距离，如果两个义原层次没有共同节点，则设置他们的距离为20。
     * 
     * @param primitive1
     * @param primitive2
     * @return
     */
    public static int disPrimitive(String primitive1, String primitive2) {
        List<Integer> list1 = Primitive.getParents(primitive1);
        List<Integer> list2 = Primitive.getParents(primitive2);
        for (int i = 0; i < list1.size(); i++) {
            int id1 = list1.get(i);
            if (list2.contains(id1)) {
                int index = list2.indexOf(id1);
                return index + i;
            }
        }
        return DEFAULT_PRIMITIVE_DIS;
    }

    /**
     * 加入一个词语
     * 
     * @param word
     */
    public static void addWord(Word word) {
        List<Word> list = ALLWORDS.get(word.getWord());

        if (list == null) {
            list = new ArrayList<Word>();
            list.add(word);
            ALLWORDS.put(word.getWord(), list);
        } else {
            list.add(word);
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        // TODO Auto-generated method stub
        BufferedReader reader = new BufferedReader(new FileReader(
                "dict/glossary.dat"));
        Set<String> set = new HashSet<String>();
/*        String line = reader.readLine();
        while (line != null) {
            //System.out.println(line);
            line = line.replaceAll("\\s+", " ");
            String[] strs = line.split(" ");
            for (int i = 0; i < strs.length; i++) {
               // System.out.print(" " + strs[i]);
            }
            System.out.println();
            set.add(strs[1]);
            line = reader.readLine();
        }*/
        System.out.println(set.size());
        for (String name : set) {
            System.out.println(name);
        }
        double simval=simWord("中国","联合国");
        System.out.println(simval);
    }
}
