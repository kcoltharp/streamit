package at.dms.kjc.rstream;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import java.util.ListIterator;
import at.dms.kjc.flatgraph.*;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import at.dms.util.Utils;

/**
 * This class converts array initializers to a bunch of assignment
 * statements.  For locals it removes the array intializer from declaration and 
 * replaces it with a new array expression of the correct size, placed at the 
 * beginning of the method (after other array decls).  For fields it remembers the 
 * assignment blocks in a hashset for later placement.
 * 
 * @author Michael Gordon
 * 
 */

public class ConvertArrayInitializers extends SLIRReplacingVisitor
{
    /** Hashset mapping String -> JBlock to perform initialization of array **/
    public HashSet<JBlock> fields;
    /** current method we are visiting **/
    private JMethodDeclaration method;
    
    private JBlock currentBlock;

    /**
     * Create a new object and visit the ir starting at node.  Convert
     * array initializers to new array expressions and remembers
     * a sequence of assignments expressions that will perform the initialization.
     *
     *
     */
    public ConvertArrayInitializers() 
    {
        System.out.println("Converting Array Initializers...");
        fields = new HashSet<JBlock>();
    }
    
    public void convertFilter(SIRFilter filter) 
    {
        for (int i = 0; i < filter.getMethods().length; i++) {
            currentBlock = new JBlock(null, new JStatement[0], null);
            filter.getMethods()[i].accept(this);
            placeArrayInitializers(filter.getMethods()[i]);
        }
    
        for (int i = 0; i < filter.getFields().length; i++) {
            filter.getFields()[i].accept(this);
        }
    }

    private void placeArrayInitializers(JMethodDeclaration meth) 
    {
        if (currentBlock.isEmpty())
            return;

        //find the correct place to place the block by bypassing the 
        //variable declaration
        JStatement[] statements = meth.getBody().getStatementArray();
        //the index where to place the initializers
        int i;

        for (i = 0; i < statements.length; i++) {
            if (!(statements[i] instanceof JVariableDeclarationStatement ||
                  statements[i] instanceof JEmptyStatement))
                break;
        }
        meth.getBody().addStatement(i, currentBlock);
    }

    public Object visitMethodDeclaration(JMethodDeclaration self,
                                         int modifiers,
                                         CType returnType,
                                         String ident,
                                         JFormalParameter[] parameters,
                                         CClassType[] exceptions,
                                         JBlock body) {
        //remember what method we are currently visiting...
        method = self;
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isGenerated()) {
                parameters[i].accept(this);
            }
        }
        if (body != null) {
            body.accept(this);
        }
        method = null;
        return self;
    }



    /**
     * Visit a local var def and remove and convert the array initializer.
     */
    public Object visitVariableDefinition(JVariableDefinition self,
                                          int modifiers,
                                          CType type,
                                          String ident,
                                          JExpression expr) {
        if (expr != null) {
            if (expr instanceof JArrayInitializer) {
                arrayInitBlock(self, (JArrayInitializer)expr, 
                               currentBlock,
                               new Vector<JIntLiteral>());
            } else {
                JExpression newExp = (JExpression)expr.accept(this);
                if (newExp!=null && newExp!=expr) {
                    self.setValue(newExp);
                }
            }
        }
        return self;
    }
    
    /**
     * visit a field decl, remove and convert the array initializer
     */
    public Object visitFieldDeclaration(JFieldDeclaration self,
                                        int modifiers,
                                        CType type,
                                        String ident,
                                        JExpression expr) {
        if (expr != null) {
            if (expr instanceof JArrayInitializer) {
                //if we have an array initializer
                JBlock initBlock = new JBlock(null, new JStatement[0], null);
                //get the block that will perform the initializers assignments
                arrayInitBlock(ident, (JArrayInitializer)expr, 
                               initBlock,
                               new Vector<JIntLiteral>());
                //remember the block
                fields.add(initBlock);
            }
            else {
                JExpression newExp = (JExpression)expr.accept(this);
                if (newExp!=null && newExp!=expr) {
                    self.setValue(newExp);
                }
        
                expr.accept(this);
            }
        }
        return self;
    }
    
    /**
     * Convert the array initializer into a sequence of assignment statements in a block
     **/
    private void arrayInitBlock(Object varAccess, JArrayInitializer init, JBlock block, 
                                Vector<JIntLiteral> indices)
    {
        for (int i = 0; i < init.getElems().length; i++) {
            //recurse through the array initializer statements, building
            //the array access indices as we recurse
            if (init.getElems()[i] instanceof JArrayInitializer) {
                Vector<JIntLiteral> indices1 = new Vector<JIntLiteral>(indices);
                indices1.add(new JIntLiteral(i));
                arrayInitBlock(varAccess, (JArrayInitializer)init.getElems()[i], block,
                               indices1);

            }
            else {
                //otherwise we have an expression that is supposed to be assigned
                //to the calculated array element
                JExpression prefix;
        
                assert varAccess instanceof String || varAccess instanceof JLocalVariable;
        
                if (varAccess instanceof String)
                    prefix = new JFieldAccessExpression((String)varAccess);
                else 
                    prefix = new JLocalVariableExpression(null,
                                                          (JLocalVariable)varAccess);
                /* RMR { the old code below will reverse the order of the
                 * dimmensions in an array expression; this is a bug!
                 *
                 //build the final array access expression
                 JArrayAccessExpression access = 
                 new JArrayAccessExpression(null, 
                 prefix,
                 new JIntLiteral(i));
                 //build the remaining, from bottom up
                 for (int j = indices.size() - 1; j >= 0; j--) {
                 access = new JArrayAccessExpression(null, 
                 access,
                 (JExpression)indices.get(j));
                 }
        
                 * the following new code will produce the dimmensions in the
                 * proper order:  the prefix is the  variable name, append
                 * to that the  first dimmension and then in  the loop add
                 * every  other  dimmension but  the  last; following  the
                 * loop, add the very  last dimmension
                 */

                //build the final array access expression, top down
                JArrayAccessExpression access = new JArrayAccessExpression(null, 
                                                                           prefix,
                                                                           new JIntLiteral(i));

                if (indices.size() > 0) {
                    access = new JArrayAccessExpression(null, 
                                                        prefix,
                                                        indices.get(0));
            
                    for (int j = 1; j <= indices.size() - 1; j++) {
                        access = new JArrayAccessExpression(null, 
                                                            access,
                                                            indices.get(j));
                    }
            
                    access = new JArrayAccessExpression(null, access, new JIntLiteral(i));
                }
                /* } RMR */
        
                //only handle constant initializers for now
                assert (Utils.passThruParens(init.getElems()[i]) instanceof JLiteral) : 
                    "Error: Currently we only handle constant array initializers.";
             
                //generate the assignment statement 
                block.addStatement(new JExpressionStatement
                                   (null,
                                    new JAssignmentExpression(null,
                                                              access,
                                                              init.getElems()[i]),
                                    null));
            }
        }
    }
    
    
    /**
     * get the dimensionality and bounds based on the array initializer
     **/
    private void getDims(JExpression expr, Vector<JIntLiteral> dims) 
    {
        if (expr instanceof JArrayInitializer) {
            JArrayInitializer init = (JArrayInitializer)expr;
            dims.add(new JIntLiteral(init.getElems().length));
            if (init.getElems().length > 0)
                getDims(init.getElems()[0], dims);
        }
    }
    
    /**
     * get the element type of an array
     **/
    private CType getBaseType(CType type) 
    {
        if (type.isArrayType()) 
            return getBaseType(((CArrayType)type).getBaseType());
        return type;
    }
    
    /**
     */
    public Object visitArrayInitializer(JArrayInitializer self,
                                        JExpression[] elems)
    {
        assert false : "Visitor should not see JArrayInitializer";
        return self;
    }
}
