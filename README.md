# Zimbra RocketChat

In this Repository you will find a simple Zimlet to connect Zimbra Collaboration with Rocket Chat, and make an amazing integration with both inside the Zimbra Web Client, it will look like:

![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/zimbra-rocket-ui.png)

# Instructions
## How to install Rocket Chat

Follow the instruction for setting up Rocket Chat using Snap on Ubuntu 18. 

- https://docs.rocket.chat/installation/snaps

## Allow API tokens to be used

    echo "CREATE_TOKENS_FOR_USERS=true" > /var/snap/rocketchat-server/common/create-tokens.env
    sudo systemctl restart snap.rocketchat-server.rocketchat-server.service

## Set up a reverse proxy

This will allow you to use TLS/SSL and configure headers needed for the integration. See the proxy-config folder for a basic example nginx/apache config.

      apt install nginx
      systemctl enable nginx   

Modify the config in `/etc/nginx/sites-enabled/default` as per the example in proxy-config folder.

      systemctl start nginx

## Choose your domain wisely

During 2020 Google and various vendors have been pushing the requirement to set cookies with the SameSite and Secure attributes.

RocketChat sets cookies on the client via Javascript and does not support configuring the SameSite attribute, which is needed for it to run on a different domain than Zimbra. This means that RocketChat needs to be installed on a subdomain similar to Zimbra. Aka zimbramail.example.com and rocketchat.example.com will work, zimbramail.example.io and rocketchat.example.com will not work.

https://security.stackexchange.com/questions/223473/for-samesite-cookie-with-subdomains-what-are-considered-the-same-site

## This Zimlet supports optional auto-login to Rocket using the Rocket API
This Zimlet can automatically log your users on to Rocket chat and even automatically create new users on Rocket chat. That way you only need to maintain the user accounts on Zimbra (full integration). Or you can maintain your Rocket accounts via LDAP or manually, but still log them on automatically (logon-only integration). Or you can just deploy only the Zimlet and let the user decide on the authentication (basic integration).
1. **Full integration**
For this you need to set-up the Java server extension copy it from https://github.com/Zimbra-Community/zimbra-rocket/releases to /opt/zimbra/lib/ext/rocket/rocket.jar ( and make sure this is the only jar in this folder) then create a text file /opt/zimbra/lib/ext/rocket/config.properties with the contents:

        adminuser=adminUsername
        adminpassword=adminPassword
        rocketurl=https://rocket.example.org
        loginurl=https://mail.example.org

This adminuser and password you should have created when you first installed Rocket. The loginurl is the place where we point users to that have not yet authenticated. This can be your SSO login page or the Zimbra login page. Don't forget `zmmailboxdctl restart`. Do not put a / at the end of rocketurl! Make sure to configure X-Frame-Options/Access-Control-Allow-Origin on the Rocket Chat server side. You must remove this headers in a reverse proxy and add the correct ones for the integration to work. See the proxy-config for a basic example nginx/apache config.

You must also configure Rocket chat like so:
![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/zimbra-rocket-iframe.png)
Be careful, as you can easily lock yourself out if something does not work. If you want more details check: https://github.com/Zimbra-Community/zimbra-rocket/wiki/Debugging

You must also configure and deploy the Zimlet:
Get a com_zimbra_rocket.zip (from Github releases) and as Zimbra user:

      zmzimletctl deploy com_zimbra_rocket.zip
      
To configure the rocketurl in the Zimlet

      zmzimletctl getConfigTemplate /opt/zimbra/zimlets-deployed/com_zimbra_rocket > /tmp/config_template.xml.tmp
      
Edit the /tmp/config_template.xml.tmp file according to your needs. Import the new configuration file by the running following command:

      zmzimletctl configure /tmp/config_template.xml.tmp

2. **Logon-only integration**
Follow the same steps as under `Full integration` except when configuring the Zimlet set `createRocketAccount` to `false`.  The Zimlet assumes you created the account on Rocket chat. So if your Zimbra account is user@example.com a Rocket user account user.example.com should exist.

3. **Basic integration**
In this case you may want to configure Rocket to use Zimbra LDAP. See steps below.

You must configure and deploy the Zimlet:
Get a com_zimbra_rocket.zip (from Github releases) and as Zimbra user:


      zmzimletctl deploy com_zimbra_rocket.zip
      
Configure the `rocketurl` in the Zimlet and set `createRocketAccount` to `false` 

      zmzimletctl getConfigTemplate /opt/zimbra/zimlets/com_zimbra_rocket > /tmp/config_template.xml.tmp
      
Edit the /tmp/config_template.xml.tmp file according to your needs. Import the new configuration file by the running following command:

      zmzimletctl configure /tmp/config_template.xml.tmp


## Debugging

To check your configuration (config.properties) for typos and make sure you have no firewall issues, you can run the test tool on your Zimbra server. This will try and read your `/opt/zimbra/lib/ext/rocket/config.properties` and make a request to your Rocket server for debugging, without needing to dig through Zimbra's log files.

     su zimbra
     cd /tmp
     wget https://github.com/Zimbra-Community/zimbra-rocket/raw/master/rocket-test/out/artifacts/rocket_test_jar/rocket-test.jar -O /tmp/rocket-test.jar
     /opt/zimbra/common/lib/jvm/java/bin/java -jar /tmp/rocket-test.jar

It should report some `adminAuthToken` and report `Configuration seems correct!`. If not, check the printed stack traces for clues as to what is wrong. `HTTP response code: 401` means username/password of admin is wrong.

In addition to this we also have https://github.com/Zimbra-Community/zimbra-rocket/wiki/Debugging for instructions on how to debug using curl.


## Make you Zimbra Admin a Rocket Admin, creation of RocketChat Admins

Once you have enabled iframe-authentication you will not be able to log-in directly using the RocketChat log-in page. In most cases you will not be able to log in to the RocketChat administrative account. To fix this you can promote a regular user account to have an admin role. Log on to Zimbra and go to the RocketChat tab of the account you wish to use as admin and verify the account name. Example `admin@zimbra.example.com` becomes `admin.zimbra.example.com` in RocketChat. Promote this user on RocketChat server like this:

      cd /snap/rocketchat-server/current
      ./bin/mongo parties --eval 'db.users.update({username:"admin.zimbra.example.com"}, {$set: {'roles' : [ "admin" ]}})'

## Separation of tenants

This Zimlet does not separate tenants from Zimbra, so all users on your Zimbra server that have access to the RocketChat Zimlet will have an account on RocketChat and will be able to see and chat with each other. Accounts on RocketChat are created if/when the user logs-in on Zimbra. To remove a user from RocketChat you have to remove them on RocketChat and de-activate them in Zimbra or disable the RocketChat Zimlet for the user you want to remove from RocketChat. 

## How to Configure Rocket Chat with Zimbra LDAP (optional)
Configure Rocket Chat with the Zimbra LDAP is really easy, and you must follow the next steps:

Go to Administration and then LDAP, and enable the LDAP option, mark the option as shown, change the LDAP hostname with your Zimbra one (the Rocket and the Zimbra must have Network visibility)
![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/rocket-001.png)

On the *Domain Base* add the next, change it with your domain: *ou=people,dc=zimbra,dc=io*
On the *Domain Search User* select the next: *uid=zimbra,cn=admins,cn=zimbra*
On the *Domain Search Password* add your LDAP Password, you can retrieve on the Zimbra server with *zmlocalconfig -s zimbra_ldap_password*
![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/rocket-002.png)

On the next fields, please fill them as shown:
![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/rocket-003.png)

On the latest fields, fill them as shown and finally click on test, and Sync users, and Save
![Zimbra Rocket](https://raw.githubusercontent.com/Zimbra-Community/zimbra-rocket/master/img/rocket-004.png)

# Troubleshooting
https://github.com/Zimbra-Community/zimbra-rocket/wiki/Debugging

========================================================================
### License
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
