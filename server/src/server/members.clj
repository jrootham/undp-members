(ns server.members
	(:gen-class)
	(:require [com.unbounce.encors :refer [wrap-cors]])
	(:use ring.adapter.jetty)
	(:require [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
         [ring.util.response :refer [response]])
	(:require [compojure.core :refer :all] [compojure.route :as route])
	(:require [clojure.java.jdbc :refer :all :as jdbc])
	(:use clojure.java.jdbc)
	(:require [clojure.string :as str])
	(:require [bcrypt-clj.auth :as auth])
	(:require [ring-debug-logging.core :refer [wrap-with-logger]])
)

(defn return-error [message]
	{:status 400 :body message}
)

(defn not-found []
	{:status 200 :body {:found false :id 0}}
)

(defn found [id]
	{:status 200 :body {:found true :id id}}
)

(defn signon [db member-name sent-password]
	(let 
		[
			query-string "SELECT member_id,password FROM members WHERE name=?"
			result (query db [query-string member-name])
		]
		(if (== 1 (count result))
			(let
				[
					data (first result)
					id (get data :member_id)
					stored-password (get data :password)
				]
				(if (auth/check-password sent-password stored-password)
					(found id)
					(not-found)
				)
			)
			(not-found)
		)
	)
)

(defn find-member [db member-name]
	(let 
		[
			query-string "SELECT member_id FROM members WHERE name=?"
			result (query db [query-string member-name])
		]
		(if (== 1 (count result))
			(let
				[
					data (first result)
					id (get data :member_id)
				]
				(found id)
			)
			(not-found)
		)
	)
)

(defn shutdown [saved-phrase given-phrase]
	(if (== 0 (compare saved-phrase given-phrase))
		(System/exit 0)
		(return-error "Bad body")
	)
)

(defroutes member-routes
	(POST "/login" [:as {db :connection {member "member" password "password"} :body}] 
		(signon db member password))
	(POST "/find" [:as {db :connection {member "member"} :body}] (find-member db member))
	(POST "/shutdown" [:as {saved-phrase :shutdown {given-phrase "shutdown"} :body}] 
		(shutdown saved-phrase given-phrase))
	(route/not-found {:status 404})
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

(defn make-wrap-db [db-url]
	(fn [handler]  
		(fn [req]   
			(with-db-connection [db {:connection-uri db-url}]
				(handler (assoc req :connection db))
			)
		)
	)
)

(defn make-insert-shutdown [shutdown]
	(fn [handler]  
		(fn [req]   
			(handler (assoc req :shutdown shutdown))
		)
	)
)

(defn make-handler [db-url shutdown] 
	(let 
		[
			wrap-db (make-wrap-db db-url)
			insert-shutdown (make-insert-shutdown shutdown)
		] 
		(-> member-routes
			(wrap-db)
			(wrap-json-body)
			(wrap-json-response)
			(insert-shutdown)
			(cors)
;			(wrap-with-logger)
		)
	)
)

(defn -main
  	"Mock NDP members server"
  	[& args]
  	(if (== 5 (count args))
		(let 
			[
				port-string (nth args 0)
				db-name (nth args 1)
				db-user (nth args 2)
				db-password (nth args 3)
				shutdown (nth args 4)
			]
			(try
				(let 
					[
						port (Integer/parseInt port-string)
						url (str "jdbc:postgresql:" db-name "?user=" db-user "&password=" db-password)
					]
					(run-jetty (make-handler url shutdown) {:port port})
				)
				(catch NumberFormatException exception 
					(println (str port-string " is not an int"))
				)
			)
		)
	  	(println "Usage: members port db-name db-user db-password shutdown-phrase")
	)
)
