package tk.barrydegraaff;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.json.JSONObject;
public class Main {

    public static void main(String[] args) {
        HttpURLConnection connection = null;
        String inputLine;
        String adminUserName;
        String adminPassword;
        String rocketURL;
        String loginurl;

        StringBuffer response = new StringBuffer();

        Properties prop = new Properties();
        try {
            FileInputStream input = new FileInputStream("/opt/zimbra/lib/ext/rocket/config.properties");
            prop.load(input);
            adminUserName = prop.getProperty("adminuser");
            adminPassword = prop.getProperty("adminpassword");
            rocketURL = prop.getProperty("rocketurl");
            loginurl = prop.getProperty("loginurl");
            input.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("We seem to have issues...");
            return;
        }

        try {
            String urlParameters = "username=" + adminUserName + "&password=" + adminPassword;
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(rocketURL + "/api/v1/login");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            connection.setUseCaches(false);
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            if (connection.getResponseCode() == 200) {
                // get response stream
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                // feed response into the StringBuilder
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // Start parsing
                JSONObject obj = new JSONObject(response.toString());
                String authToken = obj.getJSONObject("data").getString("authToken");
                String userId = obj.getJSONObject("data").getString("userId");
                String adminAuthToken = authToken;
                String adminUserId = userId;
                System.out.println("adminAuthToken" + adminAuthToken);
                System.out.println("adminUserId" + adminUserId);
                System.out.println("Configuration seems correct!");

            } else {
                System.out.println("We seem to have issues...");
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                // feed response into the StringBuilder
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                System.out.println(response.toString());
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("We seem to have issues...");
            return;
        }

    }
}
