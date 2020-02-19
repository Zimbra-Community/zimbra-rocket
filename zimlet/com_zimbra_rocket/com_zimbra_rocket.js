function com_zimbra_rocket_HandlerObject() {
   com_zimbra_rocket_HandlerObject.settings = {};
};

com_zimbra_rocket_HandlerObject.prototype = new ZmZimletBase();
com_zimbra_rocket_HandlerObject.prototype.constructor = com_zimbra_rocket_HandlerObject;
var ZimbraRocketZimlet = com_zimbra_rocket_HandlerObject;

ZimbraRocketZimlet.prototype.init = function () {
   try {
      var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;
      zimletInstance.rocketurl = zimletInstance._zimletContext.getConfig("rocketurl");
      zimletInstance.createRocketAccount = zimletInstance._zimletContext.getConfig("createRocketAccount");      
      
      if(!zimletInstance.userAccountCreateInteger)
      {
         zimletInstance.userAccountCreateInteger = 0;
      }
   
      if(zimletInstance.createRocketAccount == "true")
      {
         ZimbraRocketZimlet.prototype.createAccount();
      }
      else
      {
         ZimbraRocketZimlet.prototype.setIframe();
      }
   } catch(err)   
   {
      console.log(err);
   }
};

ZimbraRocketZimlet.prototype.setIframe = function()
{
   try {
   var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;	   
   zimletInstance.ZimbraRocketTab = zimletInstance.createApp("Rocket.Chat", "", "Rocket.Chat");
   var app = appCtxt.getApp(zimletInstance.ZimbraRocketTab);
   var appPosition = document.getElementById('skin_container_app_new_button').getBoundingClientRect();
   app.setContent('<div style="position: fixed; top:'+appPosition.y+'px; left:0; width:100%; height:92%; border:0px;"><iframe id="ZimbraRocketFrame" style="z-index:2; left:0; width:100%; height:100%; border:0px;" src=\"'+zimletInstance._zimletContext.getConfig("rocketurl")+'\"></div>');   
   } catch (err) { console.log (err)} 
};

/**
 * This method gets called by the Zimlet framework each time the application is opened or closed.
 *  
 * @param	{String}	appName		the application name
 * @param	{Boolean}	active		if true, the application status is open; otherwise, false
 */
ZimbraRocketZimlet.prototype.appActive =
function(appName, active) {
   var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;
	if (active)
   {
      document.title = 'Zimbra: ' + 'Rocket.Chat';
      //In the Zimbra tab hide the left menu bar that is displayed by default in Zimbra, also hide the mini calendar
      document.getElementById('z_sash').style.display = "none";   
      //Users that click the tab directly after logging in, will still be served with the calendar, as it is normal
      //it takes some time to be displayed, so if that occurs, try to remove the calender again after 10 seconds.
      try {
         var cal = document.getElementsByClassName("DwtCalendar");
         cal[0].style.display = "none";
      } catch (err) { setTimeout(function(){try{var cal = document.getElementsByClassName("DwtCalendar"); cal[0].style.display = "none";}catch(err){} }, 10000); }
      
      var app = appCtxt.getApp(zimletInstance.ZimbraRocketTab);
      var overview = app.getOverview(); // returns ZmOverview
      overview.setContent("&nbsp;");
      try {
         var child = document.getElementById(overview._htmlElId);
         child.parentNode.removeChild(child);
      } catch(err)
      {
         //already gone
      }
   }
   else
   {
      document.getElementById('z_sash').style.display = "block";
      try {
         var cal = document.getElementsByClassName("DwtCalendar");
         cal[0].style.display = "block";
      } catch (err) { }
   }
};

ZimbraRocketZimlet.prototype.createAccount = function()
{
   var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;	
   try{
      var xhr = new XMLHttpRequest();  
      xhr.open('GET', '/service/extension/rocket?action=createUser');
   
      xhr.onerror = function (err) {
         console.log(err);
       };
         
      xhr.onload = function (oEvent) 
      {
         ZimbraRocketZimlet.prototype.setIframe();          
      };
      xhr.send();
   } catch (err) {     
      console.log(err);
   }
};
