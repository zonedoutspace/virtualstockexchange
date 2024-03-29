/*
 * BuyScrips.java
 *
 * Created on October 24, 2008, 7:08 PM
 */

package web;

import ejb.ScripsExchangeEntity;
import ejb.ScripsExchangeEntityFacadeLocal;
import ejb.TransactionHistoryEntity;
import ejb.UsersEntity;
import ejb.UsersEntityFacadeLocal;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.servlet.*;
import javax.servlet.http.*;
import web.utils.HtmlBuilder;

/**
 *
 * @author Vaibhav
 * @version
 *
 * The servlet that generates the page for buying shares in a Scrip.
 * Shows the user all the Scrips available and needs the user to enter
 * the number of shares to be bought.
 */
public class BuyScrips extends HttpServlet {
    
    /** Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        //Creating session object
        HttpSession appSession = request.getSession(true);
        int errorcode = 0;
        
        //Doing a JNDI lookup for ScripsExchangeEntityFacade
        ScripsExchangeEntityFacadeLocal lookupExchangeEntityEntityFacade
                = (ScripsExchangeEntityFacadeLocal)lookupExchangeEntityFacade();
        
        UsersEntityFacadeLocal uef = (UsersEntityFacadeLocal)lookupUsersFacade();
        
        
        
        //Checking if the session and logged in user are valid
        if (isInvalidSession(appSession)) {
            response.sendRedirect("NewLogin");
            return;
        }
        
        
        boolean erroredSelect = false;
        boolean erroredNumNull = false;
        boolean erroredNumType = false;
        
        String scripId=request.getParameter("scripId");
        String num=request.getParameter("num");
        
        int numInt = 0;
        
        if(scripId != null && num != null) {
            System.out.println("check1");
            if((num.equals(""))) {
                erroredNumNull = true;
                System.out.println("check2");
            } else {
                try{numInt = Integer.parseInt(num);} catch(NumberFormatException e) {
                    erroredNumType = true;
                    System.out.println("check4");
                }
            }
            if(!erroredNumType && (numInt<=0)) {
                erroredNumType = true;
            }
            if((scripId.equals("--SELECT--"))) {
                erroredSelect = true;
                System.out.println("check3");
            }
        }
        
        //Adding data to queue on page submit
        if ((scripId!=null) && (num!=null) && (!erroredNumNull) && (!erroredNumType) && (!erroredSelect)) {
            
            List scrip = lookupExchangeEntityEntityFacade.findScripById(scripId);
            List user = uef.findUserById(appSession.getAttribute("userid").toString());
            
            double pricePerShare = ((ScripsExchangeEntity)scrip.get(0)).getPricePerShare();
            
            if((((ScripsExchangeEntity)scrip.get(0)).getTotalAvailable() - ((ScripsExchangeEntity)scrip.get(0)).getTotalSharesLent() - Integer.parseInt(num)) <= ((int)(((ScripsExchangeEntity)scrip.get(0)).getTotalShares()*.2))){
                errorcode = 1;
            } else if(((UsersEntity)user.get(0)).getCashHeld() < (pricePerShare*(Integer.parseInt(num)))) {
                errorcode = 2;
            } else {
                
                Queue queue = null;
                QueueConnection connection = null;
                QueueSession session = null;
                MessageProducer messageProducer = null;
                try {
                    
                    InitialContext ctx = new InitialContext();
                    
                    //Doing a JNDI lookup on the Message-driven Bean JMS queue
                    queue = (Queue) ctx.lookup("queue/mdb1");
                    QueueConnectionFactory factory =
                            (QueueConnectionFactory) ctx.lookup("ConnectionFactory");
                    connection = factory.createQueueConnection();
                    session = connection.createQueueSession(false,
                            QueueSession.AUTO_ACKNOWLEDGE);
                    messageProducer = session.createProducer(queue);
                    
                    ObjectMessage message = session.createObjectMessage();
                    
                    //Creating a TransactionHistoryEntity object, that will be sent
                    //in the JMS message
                    TransactionHistoryEntity e = new TransactionHistoryEntity();
                    
                    //Adding data to the object
                    e.setScripId(scripId);
                    e.setUserId(appSession.getAttribute("userid").toString());
                    e.setTotalShares(Integer.parseInt(num));
                    e.setTranType("Buy");
                    e.setTranDate(System.currentTimeMillis());
                    
                    //Adding message to the queue
                    message.setObject(e);
                    messageProducer.send(message);
                    messageProducer.close();
                    connection.close();
                    
                    appSession.setAttribute("message", num+" shares " +
                            "of "+((ScripsExchangeEntity)scrip.get(0)).getScripName()+" were successfully purchased");
                    
                    //Redirecting depending on the role of the user
                    if(appSession.getAttribute("userrole").equals("t")) {
                        response.sendRedirect("TraderTradeSuccess");
                    } else if(appSession.getAttribute("userrole").equals("i")) {
                        response.sendRedirect("InvestorTradeSuccess");
                    } else {
                        response.sendRedirect("RoleEmptyFailure");
                    }
                } catch (JMSException ex) {
                    ex.printStackTrace();
                } catch (NamingException ex) {
                    ex.printStackTrace();
                }
            }
            
        }
        
        
        PrintWriter out = response.getWriter();
        //output boilerplate HTML header
        out.println(HtmlBuilder.buildHtmlHeader("Buy Shares"));
        
        //main HTML content
        out.println("<p align=center><span class=\"ttitle\" style=\"580px;\"><br>Buy Shares</span><br><br>");
        
        if (errorcode == 1) {
            out.println("<br><font color=red><b>You are attempting to buy more " +
                    "shares than curently available for transactions, please try again." +
                    "</b></font><br><br>");
        }
        
        if (errorcode == 2) {
            out.println("<br><font color=red><b>You are attempting to buy shares totaling value" +
                    " greater than your cash held, please try again." +
                    "</b></font><br><br>");
        }
        
        if (erroredNumNull)
            out.println("<br><font color=red><b>Please enter the number of scrips to buy</b></font><br><br>");
        if (erroredNumType)
            out.println("<br><font color=red><b>Please enter a valid value for scrips</b></font><br><br>");
        if (erroredSelect)
            out.println("<br><font color=red><b>Please select a scrip to buy</b></font><br><br>");
        
        out.println("<form method=post>");
        
        List scrips = lookupExchangeEntityEntityFacade.findAll();
        
        //Showingall the Scrips in the system in a Select box
        out.println("<p align=center><table border=1><tr><td> Scrip Name:</td>");
        out.println("<td><select name='scripId'>");
        out.println("<option value =\"--SELECT--\")>--SELECT--</option>");
        for (Object obj : scrips) {
            ScripsExchangeEntity elem = (ScripsExchangeEntity) obj;
            out.println("<option value =" +elem.getScripId()+">"+elem.getScripName() +" </option>");
        }
        out.println("</select></td></tr>");
        
        out.println("<tr><td>Number of shares:</td><td> <input type='text' name='num' maxlength=6></td></tr>");
        out.println("<tr><td colspan=2 align=center><input type='submit' value='Buy'>&nbsp;");           
        
        if(((String)appSession.getAttribute("userrole")).equals("t")) {
            out.println("<input type=\"button\" value=\"Cancel\" " +
                    "onClick=\"window.location='TraderHome'\"/></td></tr></table></p>");            
        }
        
        if(((String)appSession.getAttribute("userrole")).equals("i")) {            
            out.println("<input type=\"button\" value=\"Cancel\" " +
                    "onClick=\"window.location='InvestorServlet'\"/></td></tr></table></p>");            
        }
        
        out.println("</form></p>");
        
        out.println("</p>");
        
        //Common Starts
        out.println(HtmlBuilder.buildHtmlFooter());
        
        out.close();
    }
    
    /**
     * Helper function to validate if the user is authorized to be at this page.
     * @param session
     * @return returns true if allowed, false if not allowed
     */
    private boolean isInvalidSession(final HttpSession session) {
        return  session.isNew() ||
                session.getAttribute("userid") == null ||
                session.getAttribute("userrole") == null ||
                ((String)session.getAttribute("userrole")).equals("a"); // only admin's CANNOT participate in exchange
    }
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
      protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        //processRequest(request, response);
               
          if (request.getQueryString() != null)
            response.sendRedirect(HtmlBuilder.DO_GET_REDIRECT_PAGE);
        else
            processRequest(request, response);
    }
    
    /** Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    }
    
    /** Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }
    // </editor-fold>
    
    /**
     * Performs JNDI lookup on ScripsExchangeEntity
     * @return Local facade of the ScripsExchangeEntity
     */
    private ScripsExchangeEntityFacadeLocal lookupExchangeEntityFacade() {
        try {
            Context c = new InitialContext();
            return (ScripsExchangeEntityFacadeLocal) c.lookup("NewsApp/ScripsExchangeEntityFacade/local");
        } catch(NamingException ne) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,"exception caught" ,ne);
            throw new RuntimeException(ne);
        }
    }
    
    private UsersEntityFacadeLocal lookupUsersFacade() {
        try {
            Context c = new InitialContext();
            return (UsersEntityFacadeLocal) c.lookup("NewsApp/UsersEntityFacade/local");
        } catch(NamingException ne) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,"exception caught" ,ne);
            throw new RuntimeException(ne);
        }
    }
}

