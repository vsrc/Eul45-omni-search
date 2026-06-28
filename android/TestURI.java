import java.net.URI;
public class TestURI {
    public static void main(String[] args) throws Exception {
        URI uri = new URI("http://192.168.1.5:8080?token=abc");
        System.out.println("Authority: " + uri.getAuthority());
        System.out.println("Query: " + uri.getRawQuery());
        
        URI uri2 = new URI("omnisearch://192.168.1.5:8080?token=abc");
        System.out.println("Authority2: " + uri2.getAuthority());
    }
}
