/*
 * BuyToCoverTransactionMessage.java
 *
 * Created on October 30, 2008, 6:51 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ejb;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.MessageDrivenContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Entity class BuyToCoverTransactionMessage
 * 
 * @author Vaibhav
 */
@MessageDriven(mappedName = "jms/BuyToCoverTransactionMessage", activationConfig =  {
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "queue/mdb5")
    })
public class BuyToCoverTransactionMessage implements MessageListener {

    @PersistenceContext
    private EntityManager em;
    
    @Resource
    private MessageDrivenContext mdc;
        
    /** Creates a new instance of SellTransactionMessage */
    public BuyToCoverTransactionMessage() {
    }

    public void onMessage(Message message) {
        ObjectMessage msg = null;
        try {
            if (message instanceof ObjectMessage) {
                msg = (ObjectMessage) message;
                TransactionHistoryEntity e = (TransactionHistoryEntity) msg.getObject();
                save(e);
            }
        } catch (JMSException e) {
            e.printStackTrace();
            mdc.setRollbackOnly();
        } catch (Throwable te) {
            te.printStackTrace();
        }
    }

    public void save(Object object) {
        List al;
        List al1;
        List al2;
        
        TransactionHistoryEntity the = (TransactionHistoryEntity) object;
        String scripId = the.getScripId();
        String userId = the.getUserId();
        int num = the.getTotalShares();
        
        ScripsExchangeEntityFacadeLocal seef = (ScripsExchangeEntityFacadeLocal)lookupExchangeEntityFacade();               
        
        al = seef.findScripById(scripId);
                
        if(al.isEmpty() != true) {
            ScripsExchangeEntity see = (ScripsExchangeEntity) al.get(0);
            int lent = see.getTotalSharesLent();
            see.setTotalSharesLent(lent-num);
            
            the.setPricePerShare(see.getPricePerShare());
            
            //Share price value reset, using marketcap/totalshares
            double newprice = (see.getMarketCap())/(see.getTotalAvailable() - num + see.getTotalSharesLent());
            see.setPricePerShare(newprice);
            see.setChange(1);
            
            seef.edit(see);             
            
        } else{//TODO: Raise exception, Scrip not found
            
        }
        
        ScripsShortedEntityFacadeLocal ssef = (ScripsShortedEntityFacadeLocal)lookupShortedEntityFacade();               
        
        al1 = ssef.findScripForUser(userId, scripId);
        
        if(al1.isEmpty() != true) {
            //Updating table
            ScripsShortedEntity sse = (ScripsShortedEntity) al1.get(0);
            int borrowed = sse.getSharesBorrowed();
            int shorted = sse.getSharesShorted();
            int returned = sse.getSharesReturned();
                    
            if((borrowed-returned)<num) {
                //TODO: Raise exception, attempt to return more than borrowed
            }
            /* if(shorted != borrowed) {
                //TODO: Warn, returning before shorting
            }*/
            else if(borrowed == shorted && shorted == returned) {
                ssef.destroy(sse);
            }
            else {
                sse.setSharesReturned(returned+num);
                ssef.edit(sse);            
            }
        } else{//TODO:Scrip not held                       
        }
                
        UsersEntityFacadeLocal uef = (UsersEntityFacadeLocal)lookupUsersFacade();
        
        al2 = uef.findUserById(userId);
        
        if(al2.isEmpty() != true) {
            //Updating table
            UsersEntity ue = (UsersEntity) al2.get(0);
            double balance = ue.getCashHeld();
            ue.setCashHeld(balance - (the.getPricePerShare() * num));            
            uef.edit(ue);
        } else{
            //TODO: Raise exception, user doesnot exist.
        }
        
        
        em.flush();
        em.persist(the);
    }
    
    
    private ScripsExchangeEntityFacadeLocal lookupExchangeEntityFacade() {
        try {
            Context c = new InitialContext();
            return (ScripsExchangeEntityFacadeLocal) c.lookup("NewsApp/ScripsExchangeEntityFacade/local");
        } catch(NamingException ne) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,"exception caught" ,ne);
            throw new RuntimeException(ne);
        }
    }
    
    private ScripsShortedEntityFacadeLocal lookupShortedEntityFacade() {
        try {
            Context c = new InitialContext();
            return (ScripsShortedEntityFacadeLocal) c.lookup("NewsApp/ScripsShortedEntityFacade/local");
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
