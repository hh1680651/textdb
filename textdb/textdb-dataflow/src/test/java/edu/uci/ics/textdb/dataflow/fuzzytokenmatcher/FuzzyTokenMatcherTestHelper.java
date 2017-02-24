package edu.uci.ics.textdb.dataflow.fuzzytokenmatcher;

import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.textdb.api.common.ITuple;
import edu.uci.ics.textdb.api.exception.TextDBException;
import edu.uci.ics.textdb.common.constants.LuceneAnalyzerConstants;
import edu.uci.ics.textdb.common.constants.TestConstants;
import edu.uci.ics.textdb.common.exception.DataFlowException;
import edu.uci.ics.textdb.dataflow.source.ScanBasedSourceOperator;
import edu.uci.ics.textdb.dataflow.utils.TestUtils;
import edu.uci.ics.textdb.storage.DataWriter;
import edu.uci.ics.textdb.storage.RelationManager;

/**
 * A helper class for FuzzyTokenMatcher's test cases.
 * It includes functions that : 
 *   create and write data to the test tables, 
 *   delete the test tables
 *   get the query result from FuzzyTokenMatcher
 *   
 * @author Zuozhi Wang
 *
 */
public class FuzzyTokenMatcherTestHelper {
    
    public static final String PEOPLE_TABLE = "fuzzytoken_test_people";
    
    /*
     * Creates the test table(s) and writes data into it(them).
     */
    public static void writeTestTables() throws TextDBException {
        RelationManager relationManager = RelationManager.getRelationManager();
        
        // create the people table and write tuples
        relationManager.createTable(PEOPLE_TABLE, "../index/test_tables/" + PEOPLE_TABLE, 
                TestConstants.SCHEMA_PEOPLE, LuceneAnalyzerConstants.standardAnalyzerString());

        DataWriter peopleDataWriter = relationManager.getTableDataWriter(PEOPLE_TABLE);
        peopleDataWriter.open();
        for (ITuple tuple : TestConstants.getSamplePeopleTuples()) {
            peopleDataWriter.insertTuple(tuple);
        }
        peopleDataWriter.close();
    }
    
    /*
     * Deletes the test table(s)
     */
    public static void deleteTestTables() throws TextDBException {
        RelationManager relationManager = RelationManager.getRelationManager();
        relationManager.deleteTable(PEOPLE_TABLE);
    }
    
    /*
     * Gets the query results from FuzzyTokenMatcher (without limit and offset).
     */
    public static List<ITuple> getQueryResults(String tableName, String query, double threshold, List<String> attributeNames) throws TextDBException {
        return getQueryResults(tableName, query, threshold, attributeNames, Integer.MAX_VALUE, 0);
    }
    
    /*
     * Gets the query results from FuzzyTokenMatcher (with limit and offset options)
     */
    public static List<ITuple> getQueryResults(String tableName, String query, double threshold, List<String> attributeNames,
            int limit, int offset) throws TextDBException {
        
        // results from a scan on the table followed by a fuzzy token matcher
        List<ITuple> scanSourceResults = getScanSourceResults(tableName, query, threshold, attributeNames, limit, offset);
        // results from index-based look-ups on the table
        List<ITuple> fuzzyTokenSourceResults = getFuzzyTokenSourceResults(tableName, query, threshold, attributeNames, limit, offset);
        
        // if limit and offset are not relevant, the results from scan source and fuzzy token source must be the same
        if (limit == Integer.MAX_VALUE && offset == 0) {
            if (TestUtils.equals(scanSourceResults, fuzzyTokenSourceResults)) {
                return scanSourceResults;
            } else {
                throw new DataFlowException("results from scanSource and fuzzyTokenSource are inconsistent");
            }
        }
        // if limit and offset are relevant, then the results can be different (since the order doesn't matter)
        // in this case, we get all the results and test if the whole result set contains both results
        else {
            List<ITuple> allResults = getFuzzyTokenSourceResults(tableName, query, threshold, attributeNames,
                    Integer.MAX_VALUE, 0);
            
            if (scanSourceResults.size() == fuzzyTokenSourceResults.size() &&
                    TestUtils.containsAll(allResults, scanSourceResults) && 
                    TestUtils.containsAll(allResults, fuzzyTokenSourceResults)) {
                return scanSourceResults;
            } else {
                throw new DataFlowException("results from scanSource and fuzzyTokenSource are inconsistent");
            }   
        }
    }
    
    /*
     * Gets the query results by scanning the table and passing the data into a FuzzyTokenMatcher.
     */
    public static List<ITuple> getScanSourceResults(String tableName, String query, double threshold, List<String> attributeNames,
            int limit, int offset) throws TextDBException {
                
        ScanBasedSourceOperator scanSource = new ScanBasedSourceOperator(tableName); 
        FuzzyTokenPredicate fuzzyTokenPredicate = new FuzzyTokenPredicate(
                query, attributeNames, RelationManager.getRelationManager().getTableAnalyzer(tableName), threshold);
        FuzzyTokenMatcher fuzzyTokenMatcher = new FuzzyTokenMatcher(fuzzyTokenPredicate);
        
        fuzzyTokenMatcher.setLimit(limit);
        fuzzyTokenMatcher.setOffset(offset);
        
        fuzzyTokenMatcher.setInputOperator(scanSource);
        
        ITuple tuple;
        List<ITuple> results = new ArrayList<>();
        
        fuzzyTokenMatcher.open();
        while ((tuple = fuzzyTokenMatcher.getNextTuple()) != null) {
            results.add(tuple);
        }  
        fuzzyTokenMatcher.close();
        
        return results;
    }
    
    /*
     * Gets the query results by using a FuzzyTokenMatcherSourceOperator (which performs index-based lookups on the table)
     */
    public static List<ITuple> getFuzzyTokenSourceResults(String tableName, String query, double threshold, List<String> attributeNames,
            int limit, int offset) throws TextDBException {
        
        FuzzyTokenPredicate fuzzyTokenPredicate = new FuzzyTokenPredicate(
                query, attributeNames, RelationManager.getRelationManager().getTableAnalyzer(tableName), threshold);
        
        FuzzyTokenMatcherSourceOperator fuzzyTokenSource = new FuzzyTokenMatcherSourceOperator(
                fuzzyTokenPredicate, tableName);
        
        fuzzyTokenSource.setLimit(limit);
        fuzzyTokenSource.setOffset(offset);
        
        ITuple tuple;
        List<ITuple> results = new ArrayList<>();
        
        fuzzyTokenSource.open();
        while ((tuple = fuzzyTokenSource.getNextTuple()) != null) {
            results.add(tuple);
        }
        fuzzyTokenSource.close();
        
        return results;
    }

}
