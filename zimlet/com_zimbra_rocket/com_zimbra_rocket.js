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
      zimletInstance.accountCreateInteger = zimletInstance._zimletContext.getConfig("accountCreateInteger");
      zimletInstance.userAccountCreateInteger = zimletInstance.getUserProperty("accountCreateInteger")
      
      if(!zimletInstance.userAccountCreateInteger)
      {
         zimletInstance.userAccountCreateInteger = 0;
      }
   
      if(zimletInstance.createRocketAccount == "true")
      {
         if(zimletInstance.accountCreateInteger > zimletInstance.userAccountCreateInteger)
         {
            zimletInstance.setUserProperty("accountCreateInteger", zimletInstance.accountCreateInteger, true);
            ZimbraRocketZimlet.prototype.createAccount();
         }
         else
         {
            ZimbraRocketZimlet.prototype.setIframe();
         }
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

/* status method show a Zimbra status message
 * */
ZimbraRocketZimlet.prototype.status = function(text, type) {
   var transitions = [ ZmToast.FADE_IN, ZmToast.PAUSE, ZmToast.FADE_OUT ];
   appCtxt.getAppController().setStatusMsg(text, type, null, transitions);
}; 

/* Called when the panel is double-clicked.
 */
ZimbraRocketZimlet.prototype.doubleClicked = function() {
   this.singleClicked();
};

/* Called when the panel is single-clicked.
 */
ZimbraRocketZimlet.prototype.singleClicked = function() {

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
         
      xhr.send();
      xhr.onreadystatechange = function (oEvent) 
      {
         if (xhr.readyState === 4)
         { 
            if (xhr.status === 200) 
            {
               if(xhr.response)
               {
                  var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;	
                  zimletInstance._dialog = new ZmDialog( { title:'Your Rocket Chat Account is created', parent:zimletInstance.getShell(), standardButtons:[DwtDialog.OK_BUTTON], disposeOnPopDown:true } );   
                  zimletInstance._dialog.setContent(
                  '<div style="width:450px; height:160px;">'+
                  'Your Rocket Chat account has been created!<br><br>'+
                  'Here are your credentials for the Rocket.Chat App on Android and iPhone:<br>'+
                  '<textarea style="width:100%" rows=2>Username: '+appCtxt.getActiveAccount().name.replace('@','.')+'\r\nPassword: '+xhr.response+'</textarea><br><br>'+
                  'Please store these credentials.<br>'+   
                  '</div>'
                  );
                  
                  zimletInstance._dialog.setButtonListener(DwtDialog.OK_BUTTON, new AjxListener(zimletInstance, zimletInstance._cancelBtn));
                  zimletInstance._dialog.setEnterListener(new AjxListener(zimletInstance, zimletInstance._cancelBtn));   
                                
                  document.getElementById(zimletInstance._dialog.__internalId+'_handle').style.backgroundColor = '#eeeeee';
                  document.getElementById(zimletInstance._dialog.__internalId+'_title').style.textAlign = 'center';
                  zimletInstance._dialog.popup(); 
                  ZimbraRocketZimlet.prototype.setIframe(); 
               }   
            }
            if (xhr.status === 500) 
            {
               if(xhr.response)
               {
                  ZimbraRocketZimlet.prototype.status("Could not create your Rocket Chat Account", ZmStatusView.LEVEL_CRITICAL);
                  ZimbraRocketZimlet.prototype.setIframe(); 
               }   
            }

         }
      }
   } catch (err) {     
      console.log(err);
   }
};

ZimbraRocketZimlet.prototype._cancelBtn =
function() {
   var zimletInstance = appCtxt._zimletMgr.getZimletByName('com_zimbra_rocket').handlerObject;
   
   try{
      zimletInstance._dialog.setContent('');
      zimletInstance._dialog.popdown();
   }
   catch (err) {}
};
