package ind.lisk;

public class Entrance {

    public static void main(String[] args) {
        try {
            DBData2XmlTransformer transformer = DBData2XmlTransformer.configFromArgs(args);
            String s = transformer.transform();
            System.out.println(s);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
