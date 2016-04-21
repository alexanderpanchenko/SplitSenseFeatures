import java.io.*;
import java.util.LinkedHashMap;
import java.util.HashSet;

public class Splitter {
    final static String LIST_SEP = "  ";
    final static String COL_SEP = "\t";
    final static String SCORE_SEP = ":";
    final static int MIN_FEATURE_LEN = 3;
    final static boolean VERBOSE = false;
    final static String stopwordsPath = "/Users/alex/Desktop/jobimatch/stopwords.csv";
    final static int MAX_FEATURE_NUM = 1000;
    final static String ENCODING = "UTF-8";

    static HashSet<String> stopwords = loadStopwords(stopwordsPath);

    static HashSet<String> loadStopwords(String stopwordsPath) {
        HashSet<String> res = new HashSet<String>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(stopwordsPath), ENCODING));
            String line =  null;
            while((line = br.readLine())!=null){
                res.add(line.trim());
            }
        } catch(Exception e) {
            System.out.printf("Error: cannot load stopwords '%s': %s", stopwordsPath, e.getMessage());
        }
        return res;
    }

    static String cleanFeature(String feature){
        boolean skipFeature =
                feature.contains("\\+") ||
                        feature.contains("+") ||
                        feature.contains("/") ||
                        feature.contains("\\") ||
                        feature.contains("$") ||
                        feature.contains("^") ||
                        feature.contains(".") ||
                        feature.contains("?") ||
                        feature.contains("(") ||
                        feature.contains(")") ||
                        feature.contains("[") ||
                        feature.contains("]") ||
                        feature.contains("{") ||
                        feature.contains("}") ||
                        feature.contains("|") ||
                        feature.contains("*");

        if (skipFeature) return "";
        else return feature.trim().replaceAll(" ", "_");
    }

    static LinkedHashMap<String, String> parseFeatures(String featuresStr){
        String[] features = featuresStr.trim().split(LIST_SEP);
        LinkedHashMap<String,String> res = new LinkedHashMap<String, String>();
        for (String f : features) {
            String[] fnameScorePair = f.trim().split(SCORE_SEP);
            if (fnameScorePair.length == 2) {
                String featureName = cleanFeature(fnameScorePair[0]);
                String featureScore = fnameScorePair[1].trim();
                if (!featureName.equals("") && !featureName.isEmpty() && featureName.length() >= MIN_FEATURE_LEN && !stopwords.contains(featureName))
                    res.put(featureName, featureScore);
                    if (!featureName.equals(featureName.toLowerCase())) res.put(featureName.toLowerCase(), featureScore);
            } else {
                if (VERBOSE) System.out.printf("Warning: cannot parse feature '%s'\n", f);
            }
        }
        return res;
    }

    static LinkedHashMap<String,String> mergeFeatures(LinkedHashMap<String,String> clusters, LinkedHashMap<String,String> features){
        LinkedHashMap<String, String> res = new LinkedHashMap<String, String>(clusters);
        for (String f: features.keySet()) {
            if (!res.containsKey(f)) {
                res.put(f, features.get(f));
            } else {
                if (VERBOSE) System.out.printf("Feature '%s' already in the collection: now '%s', proposed '%s'.\n", f, res.get(f), features.get(f));
            }
        }
        return res;
    }

    /**
     * Splits output of the SenseAggregator in the format "word<TAB>sense_id<TAB><TAB>cluster<TAB>features", where features are "feature:score<DOUBLE-SPACE>feature:score" format
     * into a directory where one file contains one line of such file with the name "word#n#sense_id".
     * */
    static void splitInventoryToWords(String inputPath, String outputDirPath, int maxFeatureNum) throws IOException {
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) outputDir.mkdirs();
        BufferedReader inputReader = new BufferedReader(new FileReader(inputPath));

        double wrongLinesNum = 0;
        double linesNum = 0;

        while (inputReader.ready()) {
            linesNum += 1;
            String line = inputReader.readLine().trim();
            if (line.isEmpty()) continue;
            String[] fields = line.split(COL_SEP);
            if (fields.length != 4) {
                System.out.printf("Warning: cannot parse the line with %d fields: '%s'\n", fields.length, line.substring(0, Math.min(line.length(), 100)));
                wrongLinesNum += 1;
                continue;
            }
            String word = fields[0].trim();
            String senseId = fields[1].trim();
            LinkedHashMap<String,String> clusterWords = parseFeatures(fields[2]);
            LinkedHashMap<String,String> features = parseFeatures(fields[3]);
            LinkedHashMap<String,String> clusterAndFeatures = mergeFeatures(clusterWords, features);

            BufferedWriter wordWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputDirPath + "/" + word + "#n#" + senseId), ENCODING));
            wordWriter.write("\n");  // needed for the evaluation script
            int featureNum = 0;
            for (String featureName: clusterAndFeatures.keySet()) {
                if (featureNum >= maxFeatureNum) break;
                featureNum++;

                if (!featureName.isEmpty() && featureName.length() >= MIN_FEATURE_LEN) {
                    wordWriter.write(featureName + " " + clusterAndFeatures.get(featureName) + "\n");
                } else {
                    if (VERBOSE) System.out.printf("Warning: cannot parse feature '%s'\n", featureName);
                }
            }
            wordWriter.close();
        }
        inputReader.close();
        System.out.printf("# wrong lines: %.3f (%.0f of %.0f)\n", wrongLinesNum/linesNum, wrongLinesNum, linesNum);
    }

    public static void main(String args[]) throws IOException {
        String inputPath;
        String outputDir;
        int maxFeatureNum = MAX_FEATURE_NUM;
        if (args.length == 2) {
            inputPath = args[0];
            outputDir = args[1];
        } else if(args.length == 3) {
            inputPath = args[0];
            outputDir = args[1];
            maxFeatureNum = Integer.parseInt(args[2]);
        } else {
            System.out.println("Usage: <inventory-csv> <outout-directory-with-splitted-files> [<max-number-of-features>]");
            return;
        }

        System.out.printf("Input inventory: %s\n", inputPath);
        System.out.printf("Output directory: %s\n", outputDir);
        System.out.printf("Max feature num.: %d\n", maxFeatureNum);
        splitInventoryToWords(inputPath, outputDir, maxFeatureNum);
    }


}
