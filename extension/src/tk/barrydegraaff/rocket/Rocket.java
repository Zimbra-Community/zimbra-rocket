/*

Copyright (C) 2018-2021  Barry de Graaff

The MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

API implementation for Zimbra -> Rocket Chat..
https://docs.rocket.chat/guides/developer/iframe-integration/authentication#iframe-url

signOn
https://zimbradev/service/extension/rocket?action=signOn
This URL needs to be configured in Rocket Chat under
Administration->Accounts->Iframe->API URL
If the user is logged into Zimbra this
url will return an auth token to Rocket to let the user login.

redirect
https://zimbradev/service/extension/rocket?action=redirect
This URL needs to be configured in Rocket Chat under
Administration->Accounts->Iframe->Iframe URL

This implementation uses HTTP GET
Administration->Accounts->Iframe->Api Method = GET

Before enabling the IFrame auth on Rocket, make sure to
create an Admin account first (usually this is done at initial
setup). You can use these credentials to debug if needed,
but they are also needed for Zimbra to perform its actions.

After enabling IFrame auth, you can no longer log-in via
the Rocket login page. You can either follow the steps here
to promote an account created by Zimbra to have an admin role.
https://rocket.chat/docs/administrator-guides/restoring-an-admin/
OR
You can create your own account first in Rocket before enabling
the integration and promote that to admin. Aka user.example.com.

Otherwise you may lock yourself out.

  cd /snap/rocketchat-server/current
  ./bin/mongo parties --eval 'db.users.update({username:"admin.zimbra.example.com"}, {$set: {'roles' : [ "admin" ]}})'

You can debug using the following commands:

createUser
Create a new user in Zimbra, make sure to set givenName and sn,
log in as that user in Zimbra, open a tab and point it to:
https://yourzimbra/service/extension/rocket?action=createUser
Creates users in Rocket chat based on their Zimbra account name.
The Zimbra Account name is mapped to the username in Rocket Chat.
(replacing @ with . so admin@example.com in Zimbra becomes
admin.example.com in Rocket)

Furthermore the users Zimbra givenName and sn (surname) are
concatenated to the Name in Rocket. Email is email in both
systems

If it returns 500, perhaps the account already exists or your admin
credentials configured in config.properties are wrong. Or the email
address is already configured on Rocket.

Try debug further with curl:

#Get admin auth token
curl https://beta.rocket.org:443/api/v1/login -d "username=adminUsername&password=adminPassword"
#Copy paste from the output

#create a user
curl -H "X-Auth-Token: from above command" \
     -H "X-User-Id: from above command" \
     -H "Content-type:application/json" \
     https://beta.rocket.org/api/v1/users.create \
     -d '{"name": "My User Name", "email": "exampleuser@zimbra.com", "password": "dfbgdyE%^&456645", "username": "exampleuser.zimbra.com"}'


Create a user token (used for login)
curl -H "X-Auth-Token: from above command" \
     -H "X-User-Id: from above command" \
     -H "Content-type:application/json" \
     https://beta.rocket.org:443/api/v1/users.createToken \
     -d '{ "username": "exampleuser.zimbra.com" }'

This command will tell you why the creation of a user failed.
*/

package tk.barrydegraaff.rocket;


import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.SoapHttpTransport;
import com.zimbra.common.soap.SoapProtocol;
import com.zimbra.cs.account.*;
import com.zimbra.cs.extension.ExtensionHttpHandler;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.zimbra.soap.JaxbUtil;
import com.zimbra.soap.account.message.AuthRequest;
import com.zimbra.soap.account.message.AuthResponse;
import org.json.JSONObject;

import com.zimbra.common.mime.MimeConstants;

import javax.mail.internet.MimeMessage;

import com.zimbra.common.mime.shim.JavaMailInternetAddress;
import com.zimbra.cs.mailbox.MailSender;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.util.JMSession;
import com.zimbra.soap.type.AccountSelector;

public class Rocket extends ExtensionHttpHandler {

    /**
     * The path under which the handler is registered for an extension.
     *
     * @return path
     */
    @Override
    public String getPath() {
        return "/rocket";
    }

    private String adminAuthToken;
    private String adminUserId;
    private String adminUserName;
    private String adminPassword;
    private String rocketURL;
    private String loginurl;
    private Boolean isMobile;
    private String enableWelcomeEmail;

    /**
     * Processes HTTP POST requests.
     *
     * @param req  request message
     * @param resp response message
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        this.initializeRocketAPI();
        resp.setHeader("Access-Control-Allow-Origin", this.rocketURL);
        resp.setHeader("Access-Control-Allow-Credentials", "true");
        String token;
        try {
            AccountSelector acctSel = new AccountSelector(com.zimbra.soap.type.AccountBy.name, username);
            SoapHttpTransport transport = new SoapHttpTransport(getSoapUrl());
            AuthRequest authReq = new AuthRequest(acctSel, password);
            Element authRespElem = transport.invoke(JaxbUtil.jaxbToElement(authReq, SoapProtocol.SoapJS.getFactory()));
            AuthResponse authResp = JaxbUtil.elementToJaxb(authRespElem);
            String authTokenStr = authResp.getAuthToken();

            AuthToken authToken = AuthToken.getAuthToken(authTokenStr);
            Provisioning prov = Provisioning.getInstance();
            Account zimbraAccount = prov.getInstance().getAccountById(authToken.getAccountId());

            this.createUser(zimbraAccount);
            token = this.setUserAuthToken(zimbraAccount.getName().replace("@", "."));
            if (!"".equals(token)) {
                resp.setHeader("Content-Type", "text/html");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write("<html><head></head><body><script>\r\nwindow.parent.postMessage({\r\n  event: 'login-with-token',\r\n  loginToken: '" + token + "'\r\n}, '" + this.rocketURL + "');\r\n</script></body>");
            } else {
                responseWriter("error", resp, null);
            }
        } catch (Exception ex) {
            //not logged in
            responseWriter("error", resp, null);
            return;
        }
    }

    public static String getSoapUrl() throws ServiceException {
        String scheme;
        Provisioning prov = Provisioning.getInstance();
        Server server = prov.getLocalServer();
        String hostname = server.getServiceHostname();
        int port;
        port = server.getIntAttr(Provisioning.A_zimbraMailSSLPort, 0);
        if (port > 0) {
            scheme = "https";
        } else {
            port = server.getIntAttr(Provisioning.A_zimbraMailPort, 0);
            scheme = "http";
        }
        return scheme + "://" + hostname + ":" + port + "/service/soap";
    }

    /**
     * Processes HTTP GET requests.
     *
     * @param req  request message
     * @param resp response message
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String authTokenStr = null;
        Account zimbraAccount = null;
        String userAgent = req.getHeader("User-Agent").toString().toLowerCase();
        this.isMobile = false;
        if (userAgent.contains("android") || userAgent.contains("iphone") || userAgent.contains("electron")) {
            this.isMobile = true;
        }

        //Just read a cos value to see if its a valid user
        try {
            Cookie[] cookies = req.getCookies();
            for (int n = 0; n < cookies.length; n++) {
                Cookie cookie = cookies[n];

                if (cookie.getName().equals("ZM_AUTH_TOKEN")) {
                    authTokenStr = cookie.getValue();
                    break;
                }
            }

            if (authTokenStr != null) {
                AuthToken authToken = AuthToken.getAuthToken(authTokenStr);
                Provisioning prov = Provisioning.getInstance();
                zimbraAccount = prov.getInstance().getAccountById(authToken.getAccountId());
                Cos cos = prov.getCOS(zimbraAccount);
                Set<String> allowedDomains = cos.getMultiAttrSet(Provisioning.A_zimbraProxyAllowedDomains);
            } else {
                responseWriter("unauthorized", resp, null);
                return;
            }

        } catch (Exception ex) {
            //not logged in
            responseWriter("unauthorized", resp, null);
            return;
        }

        final Map<String, String> paramsMap = new HashMap<String, String>();

        if (req.getQueryString() != null) {
            String[] params = req.getQueryString().split("&");
            for (String param : params) {
                String[] subParam = param.split("=");
                paramsMap.put(subParam[0], subParam[1]);
            }
        } else {
            responseWriter("Configure the Zimlet to complete the installation.", resp, null);
            return;
        }

        //Initializes adminAuthToken, adminUserId, adminUserName, adminPassword, rocketURL on this instance
        if (this.initializeRocketAPI()) {
            switch (paramsMap.get("action")) {
                case "createUser":
                    if (this.createUser(zimbraAccount)) {
                        resp.setHeader("Content-Type", "text/plain");
                        responseWriter("ok", resp, null);
                    } else {
                        responseWriter("error", resp, null);
                    }
                    break;
                case "signOn":
                    String token;
                    token = this.setUserAuthToken(zimbraAccount.getName().replace("@", "."));
                    if (!"".equals(token)) {
                        resp.setHeader("Content-Type", "application/json");
                        responseWriter("ok", resp, "{\"loginToken\":\"" + token + "\"}");
                    } else {
                        responseWriter("error", resp, null);
                    }
                    break;
                case "redirect":
                    resp.setHeader("Content-Type", "text/html");
                    responseWriter("ok", resp, "<html><head></head><body><div style=\"background-color:white;color:black;padding:10px\">Please <a target=\"_blank\" href=\"" + this.loginurl + "\">Log in</a>.</div></body>");
                    break;

            }
        } else {
            responseWriter("error", resp, null);
        }
    }

    private void responseWriter(String action, HttpServletResponse resp, String message) {
        try {
            this.initializeRocketAPI();
            resp.setHeader("Access-Control-Allow-Origin", this.rocketURL);
            resp.setHeader("Access-Control-Allow-Credentials", "true");
            switch (action) {
                case "ok":
                    resp.setStatus(HttpServletResponse.SC_OK);
                    if (message == null) {
                        resp.setHeader("Content-Type", "text/plain");
                        resp.getWriter().write("OK");
                    } else {
                        resp.getWriter().write(message);
                    }
                    break;
                case "unauthorized":
                    if (!this.isMobile) {
                        resp.setHeader("Content-Type", "text/html");
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().write("<html><head></head><body><div style=\"background-color:white;color:black;padding:10px\">Please <a target=\"_blank\" href=\"" + this.loginurl + "\">Log in</a>.</div></body>");
                    } else {
                        resp.setHeader("Content-Type", "text/html");
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.getWriter().write("<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body><div style='background-color:white;color:black;padding:10px;font-family:sans-serif'><form action='/service/extension/rocket' method='POST'>  <label style='display: inline-block;width:100px' for='username'>User name:</label>  <input type='text' id='username' name='username'><br><br>  <label style='display: inline-block;width:100px' for='password'>Password:</label>  <input type='password' id='password' name='password'><br><br>  <input type='submit' value='Submit'></form></div></body>");
                    }
                    break;
                case "error":
                    resp.setHeader("Content-Type", "text/plain");
                    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    resp.getWriter().write("The request did not succeed successfully.");
                    break;
            }
            resp.getWriter().flush();
            resp.getWriter().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Implements: https://beta.zetalliance.org:443/api/v1/login
     * Since we do not store the token, we may run into:
     * Delete obsolete tokens every 1 hour #7812
     * https://github.com/RocketChat/Rocket.Chat/pull/7812
     * One work-around would be calling the logout api.
     */
    public Boolean initializeRocketAPI() {
        HttpURLConnection connection = null;
        String inputLine;
        StringBuffer response = new StringBuffer();

        Properties prop = new Properties();
        try {
            FileInputStream input = new FileInputStream("/opt/zimbra/lib/ext/rocket/config.properties");
            prop.load(input);
            this.adminUserName = prop.getProperty("adminuser");
            this.adminPassword = prop.getProperty("adminpassword");
            this.rocketURL = prop.getProperty("rocketurl");
            this.loginurl = prop.getProperty("loginurl");
            this.enableWelcomeEmail = prop.getProperty("enableWelcomeEmail"); //may be null
            input.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        try {
            String urlParameters = "username=" + this.adminUserName + "&password=" + this.adminPassword;
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(this.rocketURL + "/api/v1/login");
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
                this.adminAuthToken = authToken;
                this.adminUserId = userId;
                return true;

            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Implements: https://rocket.chat/docs/developer-guides/rest-api/users/createtoken/
     * Since we do not store the token, we may run into:
     * Delete obsolete tokens every 1 hour #7812
     * https://github.com/RocketChat/Rocket.Chat/pull/7812
     * One work-around would be calling the logout api.
     * For debugging purpose you can get the list of users by copy pasting the following in the browser console,
     * you need to be logged into rocket for it to work
     * var xhr = new XMLHttpRequest();
     * xhr.open('GET', 'https://rocketserver:443/api/v1/directory?query={"type": "users"}');
     * xhr.setRequestHeader ("X-Auth-Token", "admin auth token");
     * xhr.setRequestHeader ("X-User-Id", "admin user id");
     * xhr.setRequestHeader ("Content-type", "application/json");
     * xhr.send();
     */
    public String setUserAuthToken(String username) {
        HttpURLConnection connection = null;
        String inputLine;
        StringBuffer response = new StringBuffer();
        try {

            String urlParameters = "{ \"username\": \"" + username + "\" }";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(this.rocketURL + "/api/v1/users.createToken");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            connection.setRequestProperty("X-Auth-Token", this.adminAuthToken);
            connection.setRequestProperty("X-User-Id", this.adminUserId);

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
                return authToken;

            } else {
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    /**
     * Implements: https://rocket.chat/docs/developer-guides/rest-api/users/create/
     */
    public Boolean createUser(Account account) {
        String email = account.getName();
        String name = account.getGivenName() + " " + account.getSn();
        String username = account.getName().replace("@", ".");

        HttpURLConnection connection = null;
        String password = newPassword();
        String inputLine;
        StringBuffer response = new StringBuffer();
        try {

            String urlParameters = "{\"name\": \"" + name + "\", \"email\": \"" + email + "\", \"password\": \"" + password + "\", \"username\": \"" + username + "\"}";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;
            URL url = new URL(this.rocketURL + "/api/v1/users.create");
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("charset", "utf-8");
            connection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            connection.setRequestProperty("X-Auth-Token", this.adminAuthToken);
            connection.setRequestProperty("X-User-Id", this.adminUserId);

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
                String success = obj.getString("success");
                if ("true".equals(success)) {
                    sendConfirmation(account, username, password);
                    return true;
                } else {
                    return false;
                }

            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendConfirmation(Account account, String username, String password) {
        try {
            if (!"false".equals(this.enableWelcomeEmail)) {
                MimeMessage mm = new Mime.FixedMimeMessage(JMSession.getSmtpSession(account));
                String to = account.getName();

                mm.setRecipient(javax.mail.Message.RecipientType.TO, new JavaMailInternetAddress(to));
                mm.setContent("Your Rocket.Chat account has been created!<br><br>You must log-on to Rocket.Chat using your Zimbra credentials.<br>For changes to crucial settings inside RocketChat you may need these additional credentials:<br><br>Username: " + username + "<br>Password: " + password, MimeConstants.CT_TEXT_HTML);
                mm.setSubject("Welcome to Rocket Chat");
                mm.saveChanges();

                Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account);
                MailSender mailSender = mbox.getMailSender();

                mailSender.setSaveToSent(false);
                mailSender.sendMimeMessage(null, mbox, mm);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*May need better randomness
     * */
    public String newPassword() {
        String password = "";
        String characters = "aA1bB2cC3dD4eE5fF6gG8hH9iI0jJkKlLmMnNoOpP&qQrRsStT_uUvV-wWxX+yYzZ";
        for (int i = 0; i < 10; i++) {
            password = password + " ";
        }
        Random random = new Random();
        for (int i = 0; i < password.length(); i++) {
            if (i == 0) {
                password = String.valueOf(characters.charAt(random.nextInt(65))) + password.substring(1, password.length());
            } else {
                password = password.substring(0, i) + String.valueOf(characters.charAt(random.nextInt(65))) + password.substring(i + 1, password.length());
            }
        }
        return password.toString();
    }

}
