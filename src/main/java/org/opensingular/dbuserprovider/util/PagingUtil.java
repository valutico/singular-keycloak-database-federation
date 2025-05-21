package org.opensingular.dbuserprovider.util;

import org.opensingular.dbuserprovider.persistence.RDBMS; // Kept for method signature compatibility

public class PagingUtil {

    public static class Pageable {
        private final int firstResult;
        private final int maxResults;

        public Pageable(int firstResult, int maxResults) {
            this.firstResult = firstResult;
            this.maxResults = maxResults;
        }

        public int getFirstResult() {
            return firstResult;
        }

        public int getMaxResults() {
            return maxResults;
        }
    }

    public static String formatScriptWithPageable(String query, Pageable pageable, RDBMS rdbms) {
        // This method previously modified the SQL string to include pagination clauses.
        // It has been refactored. The expectation is that the calling code (e.g., UserRepository)
        // will now use a Hibernate Query object (or similar PreparedStatement mechanism)
        // and apply pagination using methods like query.setFirstResult(pageable.getFirstResult())
        // and query.setMaxResults(pageable.getMaxResults()).

        // The 'pageable' and 'rdbms' parameters are currently unused in this pass-through version.
        // They are kept in the signature to minimize immediate changes to the caller's signature.
        
        if (query == null) {
            // Consider throwing an IllegalArgumentException or returning an empty string
            // depending on desired behavior for null queries.
            return null; 
        }
        
        // Simply return the original query string.
        return query;
    }
}
