/*
 * AddScripServlet.java
 *
 * Created on October 26, 2008, 7:55 PM
 */

package web;

import ejb.LoginEntity;
import ejb.LoginEntityFacadeLocal;
import ejb.ScripsExchangeEntity;
import ejb.ScripsExchangeEntityFacadeLocal;
import ejb.UsersEntity;
import ejb.UsersEntityFacadeLocal;
import java.io.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import javax.servlet.*;
import javax.servlet.http.*;
import web.utils.HtmlBuilder;

/**
 *
 * @author jmoral
 * @version
 */
public class AddScripServlet extends HttpServlet {
    
    /** Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        
        HttpSession session = request.getSession(true);
        if (isInvalidSession(session)) {
            response.sendRedirect("NewLogin");
            return;
        }
        
        String userid = (String)session.getAttribute("userid");
        
        PrintWriter out = response.getWriter();
        HashMap<String, String> parameterMap = new HashMap<String, String>();
        parameterMap.put("scripid",             request.getParameter("scripid"));
        parameterMap.put("scripname",           request.getParameter("scripname"));
        parameterMap.put("totalshares",         request.getParameter("totalshares"));
        parameterMap.put("marketcap",           request.getParameter("marketcap"));
        
        boolean erroredScripExists = false;
        boolean erroredScripNameMax = false;
        boolean erroredScripNameText = false;
        boolean erroredBlankFields = false;
        boolean erroredNumTotalShares = false;
        boolean erroredNumMarketCap = false;
        boolean erroredScripIDMin = false;
        boolean erroredScripIDMax = false;
        boolean erroredScripIDText = false;
        int numInttshare = 0;
        int numIntpshare = 0;
        
        if (HtmlBuilder.isFormSubmitted(parameterMap)) {
            
            if(HtmlBuilder.hasBlankFields(parameterMap)) {
                erroredBlankFields = true;
            }else{
                try{ numInttshare = Integer.parseInt(parameterMap.get("totalshares")); } 
                catch(NumberFormatException e) { erroredNumTotalShares = true; }
                
                try { numIntpshare = Integer.parseInt(parameterMap.get("marketcap")); }
                catch(NumberFormatException e) { erroredNumMarketCap = true; }
            }
            if(!erroredNumTotalShares && numInttshare <= 0) {
                erroredNumTotalShares = true;
            }
            if(!erroredNumMarketCap && numIntpshare <= 0) {
                erroredNumMarketCap = true;
            }
            
            // check field lengths for overflow attacks.
            if (parameterMap.get("scripname").length() > 40)
                erroredScripNameMax = true;
            if (parameterMap.get("scripid").length() < 2)
                erroredScripIDMin = true;
            if (parameterMap.get("scripid").length() > 16)
                erroredScripIDMax = true;
            
            if (!HtmlBuilder.isValidID(parameterMap.get("scripid")))
                erroredScripIDText = true;
            if (!HtmlBuilder.isValidScripName(parameterMap.get("scripname")))
                erroredScripNameText = true;
            
            if (!erroredBlankFields && !erroredNumTotalShares && !erroredNumMarketCap && 
                !erroredScripNameMax && !erroredScripIDMin && !erroredScripIDMax && !erroredScripIDText && !erroredScripNameText)
            {
                int totalSharesInt              = Integer.parseInt(parameterMap.get("totalshares"));
                double marketCapDbl             = Double.parseDouble(parameterMap.get("marketcap"));
                double pricePerShareDbl         = marketCapDbl / (double)totalSharesInt;

                ScripsExchangeEntityFacadeLocal scripsEntityFacade = (ScripsExchangeEntityFacadeLocal) lookupScripsEntityFacade();
                if (scripsEntityFacade.find(parameterMap.get("scripid")) != null) {
                    erroredScripExists = true;
                } else {
                    ScripsExchangeEntity scripsEntity =  new ScripsExchangeEntity(parameterMap.get("scripid"), parameterMap.get("scripname"),
                            totalSharesInt, totalSharesInt, marketCapDbl, pricePerShareDbl);

                    scripsEntityFacade.create(scripsEntity);
                    
                    session.setAttribute("message", parameterMap.get("scripname")+" was successfully added to the exchange");                   
                    response.sendRedirect("AdminSuccessServlet");
                }
            }
        }
        
        printForm(out, request, response, 
                    erroredScripExists, erroredBlankFields, erroredNumTotalShares, erroredNumMarketCap, 
                    erroredScripNameMax, erroredScripIDMin, erroredScripIDMax, erroredScripIDText, erroredScripNameText);
    }
    
    private boolean isInvalidSession(final HttpSession session) {
        return  session.isNew() ||
                session.getAttribute("userid") == null ||
                session.getAttribute("userrole") == null ||
                !((String)session.getAttribute("userrole")).equals("a");
    }
    
    private void printForm(PrintWriter out, final HttpServletRequest request, final HttpServletResponse response, 
                    final boolean erroredScripExists, 
                    final boolean erroredNumNull, 
                    final boolean erroredNumTotalShares, 
                    final boolean erroredNumMarketCap,
                    final boolean erroredScripNameMax, 
                    final boolean erroredScripIDMin, 
                    final boolean erroredScripIDMax,
                    final boolean erroredScripIDText,
                    final boolean erroredScripNameText) throws IOException 
    {
        out.println(HtmlBuilder.buildHtmlHeader("Add Scrip"));
        out.println("<center><span class=\"ttitle\" style=\"580px;\"><br>Add Scrip Form</span><br><br>");
        
        if (erroredScripExists)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.SCRIP_EXISTS);
        if (erroredNumNull)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_BLANK);
        if (erroredNumTotalShares)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_TOTAL_SHARES);
        if (erroredNumMarketCap)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_MARKET_CAP);
        if (erroredScripNameMax)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_SCRIPNAME_MAX);
        if (erroredScripIDMin)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_SCRIPID_MIN);
        if (erroredScripIDMax)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_SCRIPID_MAX);
        if (erroredScripIDText)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_ID_TEXT);
        if (erroredScripNameText)
            HtmlBuilder.printErrorMessage(out, HtmlBuilder.ERRORS.INVALID_SCRIPNAME_TEXT);
        
        out.println("<br/><form method=post>");
        
        out.println("<table width=350px cellpadding=4px border=1>");
        out.println("<tr><td width=150px>Scrip Id:</td><td><input type='text' name='scripid' maxlength=16></td></tr>");
        out.println("<tr><td>Scrip Name:</td><td><input type='text' name='scripname' maxlength=40></td></tr>");
        out.println("<tr><td>Total Shares:</td><td><input type='text' name='totalshares' maxlength=9></td></tr>");
        out.println("<tr><td>Market Cap:</td><td><input type='text' name='marketcap' maxlength=9></td></tr>");
        out.println("<tr><td align=center colspan=2><input type='submit' value='Add Scrip'>&nbsp; ");
        out.println("<input type=\"button\" value=\"Cancel\" onClick=\"window.location='AdminServlet'\"/></td></tr>");
        out.println("</table>");
        out.println("</form></center>");
        
        out.println(HtmlBuilder.buildHtmlFooter());
        out.close();
    }
    
    private ScripsExchangeEntityFacadeLocal lookupScripsEntityFacade() {
        try {
            Context c = new InitialContext();
            return (ScripsExchangeEntityFacadeLocal) c.lookup("NewsApp/ScripsExchangeEntityFacade/local");
        } catch(NamingException ne) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,"exception caught" ,ne);
            throw new RuntimeException(ne);
        }
    }
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
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
}
