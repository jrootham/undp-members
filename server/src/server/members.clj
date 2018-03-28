(ns server.members
  (:gen-class)
	(:require [com.unbounce.encors :refer [wrap-cors]])
	(:use ring.adapter.jetty)
	(:require [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
         [ring.util.response :refer [response]])
	(:require [ring.middleware.session :refer[wrap-session]])
	(:require [compojure.core :refer :all])
	(:require [clojure.java.jdbc :refer :all :as jdbc])
	(:use clojure.java.jdbc)
	(:require [clojure.string :as str])
	(:require [ring-debug-logging.core :refer [wrap-with-logger]])
)

(defn signon [db]
	"foo"
)

(defroutes member-routes
	(POST "/signon" [:as {db :connection}] (signon db))
)

(defn make-wrap-db [db-url]
	(fn [handler]  
		(fn [req]   
			(with-db-connection [db {:connection-uri db-url}]
				(handler (assoc req :connection db))
			)
		)
	)
)

(defn cors [handler]
	(let [cors-policy
		    { 
		    	:allowed-origins :match-origin
				:allowed-methods #{:post}
				:request-headers #{"Accept" "Content-Type" "Origin"}
				:exposed-headers nil
				:allow-credentials? true
				:origin-varies? false
				:max-age nil
				:require-origin? true
				:ignore-failures? false
		    }
     	]

     	(wrap-cors handler cors-policy)
     )
)

(defn make-handler [db-url] 
	(let [wrap-db (make-wrap-db db-url)] 
		(-> member-routes
			(wrap-db)
			(wrap-json-body)
			(wrap-json-response)
			(wrap-session)
			(cors)
;			(wrap-with-logger)
		)
	)
)

(defn get-env [name]
	(let [value (System/getenv name)]
		(if (nil? value)
			(println (str "Evironment variable " name " is undefined"))
		)
		value
	)
)

(defn -main
  	"Cabal voting server"
  	[& args]
  	(if (== 0 (count args))
		(let [url (get-env "JDBC_DATABASE_URL") 
				portString (get-env "PORT")]
			(if (and (some? url) (some? portString))
				(try
					(let [port (Integer/parseInt portString)]
						(run-jetty (make-handler url) {:port port})
					)
					(catch NumberFormatException exception 
						(println (str portString " is not an int"))
					)
				)
			)
		)  	
	  	(println "This programme has no arguments")
	)
 )
