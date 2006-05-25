package gnu.cajo.utils.extra;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.Serializable;
import gnu.cajo.invoke.Remote;
import java.rmi.RemoteException;

/*
 * cajo object oriented rule engine
 * Copyright (C) 2006 John Catherino
 * The cajo project: https://cajo.dev.java.net
 *
 * For issues or suggestions mailto:cajo@dev.java.net
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, at version 2.1 of the license, or any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You can receive a copy of the GNU Lesser General Public License from their
 * website, http://fsf.org/licenses/lgpl.html; or via snail mail, Free
 * Software Foundation Inc., 51 Franklin Street, Boston MA 02111-1301, USA
 */

/**
 * This class is a cajo-based reformulation of rule-based reasoning systems,
 * for declarative programming. This engine does not suffer from the large
 * memory requirements associated with the Rete Algorithm, as it offloads
 * each rule's state and inference to distributed JVMs. The engine is also
 * serialisable; to allow it to scale, as well as providing both modularity,
 * and redundancy.<p>
 * Only the RuleEngine ontology is stored at the server host. This makes the
 * creation of extremely large distributed rule bases both possible, and
 * very simple. The applciation specific knowledge bases are offloaded
 * to the clients as well.<p>
 * The application of fuzzy facts, and fuzzy predicates is not only easily
 * supported, but is in fact very <i>highly</i> recommended.<p>
 * <i><u>Note</u>:</i> to compile this class, it is best to set the java
 * compiler's <tt>-source</tt> switch to <tt>1.2</tt>.
 *
 * @version 1.0, 11-May-06
 * @author John Catherino
 */
public class RuleEngine implements Serializable {
   /**
    * This is a good base class for building rules. It minimises load
    * on the rule engine, and properly manages asynchronous change without
    * synchronisation, or deadlock problems. It is highly recommended to
    * extend this class for your rules. The collection of registered rules
    * constitute the <i>knowledge base</i> of the rule engine.
    */
   public static class Rule implements Serializable, Runnable {
      /**
       * The rule specific local fact base on which to infer.
       */
      protected Hashtable facts = new Hashtable();
      /**
       * This flag indicates that asynchronous modification has been
       * made to the fact base. <i><u>Very Important</u>:</i>
       * The <i>first</i> instruction of the infer method should be to
       * set it false.
       */
      protected boolean change;
      /**
       * The local reasoning thread. It is lazily instantiated, and can
       * be shut down by calling its interrupt method, if necessary.
       * It's job is to call the infer method whenever there is a
       * change (addidion, removal, modification) to the local fact
       * base.
       */
      protected transient Thread thread;
      /**
       * This method is completely rule-specific. It is called when there
       * has been a change to the local fact base. It should first
       * reset the change flag, then evaluate its state to make decisions.
       * It is possible for the fact base to be actively changing,
       * while this method is executing. This can be detected by monitoring
       * the state of the change flag.
       */
      protected void infer() { change = false; }
      /**
       * This method is invoked by the rule engine to make a change,
       * addition or modification, to the local fact base. It will
       * notify the local processing thread, creating it if necessary, and
       * return. Its run time is designed to be as short as possible, and
       * should not be overridden, unless it is absolutely critical.
       * @throws Exception An arbitrary exception may be thrown by the
       * positOk method of this class, if it does not want the fact to
       * be changed.
       */
      public synchronized void posit(Object fact, Object keys[])
         throws Exception {
         Object element = facts.get(keys[0]);
         for (int i = 1; i < keys.length; i++) {
            Object temp = ((Hashtable)element).get(keys[i]);
            if (temp == null) { // if no path, make one!
               if (i < keys.length - 1) {
                  temp = new Hashtable();
                  ((Hashtable)element).put(keys[i], temp);
               }
            }
            if (i < keys.length -1) element = temp;
         }
         ((Hashtable)element).put(keys[keys.length - 1], fact);
         change = true;
         if (thread == null) {
            thread = new Thread(this);
            thread.start();
         } else notify();
      }
      /**
       * This is the local change processing thread. It waits until there
       * has been a change to the local fact base, and invokes the
       * infer method. It is used to offload the reasoning processing
       * from the change invocation thread. It is created automatically,
       * on the first change to this rule.
       */
      public void run() {
         try {
            while (true) {
               infer();
               synchronized(this) { while(!change) wait(); }
            }
         } catch(InterruptedException x) {}
      }
   }
   /**
    * This field represents the set of listening rules registered in the
    * engine's ontology. The element stored under each key can be either a
    * Vector, indicating a collection of listening rules, or another
    * Hashtable, indicating an additional dimension to the knowledge base.
    * This allows for an n-ary dimensional organisation of rules references,
    * structured about keys. Rules are generally assumed to be references to
    * objects in remote JVMs, but that can be local object references, if
    * necessary. Multiple rules can be assigned to the same dimension, just
    * as the same rule can be assigned to multiple dimension.
    */
   protected final Hashtable rules = new Hashtable();
   /**
    * This constructor performs no function, as the operation of the rule
    * engine is completely runtime dependent.
    */
   public RuleEngine() {}
   /**
    * This method is used to assert, or modify, a fact. The engine will
    * search its ontology for suitable listeners, and notify them.
    * Each rule will be notified serially, by invoking its public
    * <tt>posit</tt> method, with the fact object, and the collection of
    * keys. If a rule instance determines all of its criteria necessary for
    * firing has been met, then it is the responsibility of that rule to
    * carry it out.<p>
    * <i><u>Note</u>:</i> Rule <tt>posit</tt> invocations are expected to
    * return as quickly as possible to ensure maximum performance of the
    * engine. Typically rule objects will save the fact argument in a queue,
    * notify a waiting local thread, and return. A rule generally will
    * register for multiple fact types, therefore its posit method most
    * likely <i>will</i> be invoked reentrantly. Also, if the invocation
    * of the rule results in an Exception, it will be removed from the
    * collection of listeners for that element.
    * @param fact The fact that is being asserted or changed.
    * @param keys The chain of keys leading to the element of interest
    * in the ontology.
    * @throws NullPointerException if either the fact is null.
    */
// if a key is null, get all elements, and recurse, null == SELECT *
   public void posit(Object fact, Object keys[]) throws Exception {
      Object element = rules.get(keys[0]);
      for (int i = 1; i < keys.length; i++)
         element = ((Hashtable)element).get(keys[i]);
      Object rulez[] = ((Vector)element).toArray();
      for (int i = 0; i < rulez.length; i++) try {
         Remote.invoke(rulez[i], "posit", new Object[] { fact, keys });
      } catch(RemoteException x) { ((Vector)element).remove(rulez[i]); }
   }
   /**
    * This method is used to register a rule instance with the ontology.
    * The rule engine will store the rule reference, to have its
    * <tt>posit</tt> method invoked, as the state of the fact of interest
    * changes. It is worth mentioning, a single rule instance is often used
    * to monitor multiple types of facts.
    * @param rule The rule to be notified when the fact state changes. It is
    * typically a reference to an object in a remote JVM, but it can be to
    * a local object, or even a proxy object.
    * @param keys The chain of keys leading to the element of interest
    * in the fact base.
    */
   public void add(Object rule, Object keys[]) {
// if a key is null, get all elements, and recurse, null == SELECT *
      Object element = rules.get(keys[0]);
      for (int i = 1; i < keys.length - 1; i++) {
         Object temp = ((Hashtable)element).get(keys[i]);
         if (temp == null) { // if no path, make one!
            if (i < keys.length - 1) {
               temp = new Hashtable();
               ((Hashtable)element).put(keys[i], temp);
            }
         }
         element = temp;
      }
      Object o = ((Hashtable)element).get(keys[keys.length - 1]);
      if (o == null) {
         o = new Vector();
         ((Hashtable)element).put(keys[keys.length - 1], o);
      }
      ((Vector)o).add(rule);
   }
   /**
    * This method is used to unregister a rule instance for a type of 
    * fact. The rule engine will delete the rule reference from its
    * ontology.
    * @param rule The rule to be removed from the engine. It is typically a
    * reference to an object in a remote JVM, but it can be to a local
    * object, or even a proxy object.
    * @param keys The chain of keys leading to the element of interest
    * in the ontology.
    */
   public void remove(Object rule, Object keys[]) {
// if a key is null, get all elements, and recurse, null == SELECT *
      Object element = rules.get(keys[0]);
      for (int i = 1; i < keys.length - 1; i++)
         element = ((Hashtable)element).get(keys[i]);
      Vector v = (Vector)((Hashtable)element).get(keys[keys.length - 1]);
      v.remove(rule);
   }
}