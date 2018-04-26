(ns fill.main
  (:gen-class)
	(:require [clojure.java.jdbc :refer :all :as jdbc])
	(:use clojure.java.jdbc)
	(:require [clojure.java.io :as io])
	(:require [clojure.string :as str])
	(:require [clojure-csv.core :as csv])
	(:require [bcrypt-clj.auth :as auth])
)

(defn make-fill-row [db]
	(fn [row]
		(let 
			[
				member-name (first row)
				password (auth/crypt-password (second row))
				email (second (rest row))
			]
			(insert! db :members {:name member-name, :password password, :email email})
		)
	)
)

(defn update-data [db-uri data]
	(with-db-connection [db {:connection-uri db-uri}]
		(let [fill-row (make-fill-row db)] 
			(doseq [row (csv/parse-csv data)] (fill-row row))
		)
	)
)

(defn get-uri [user db-name]
	(let
		[
	 		_ (println "database password:")
	  		password (read-line)
	  		db-password (str/replace password " " "+")
		]
		(str "jdbc:postgresql://localhost:5432/" db-name "?user=" user "&password=" db-password)
	)
)

(defn -main
  "Fill members database"
  [& args]
  (if (== 3 (count args))
	  (let 
	  	[
	  		user (first args)
	  		db-name (second args)
	  		data (slurp (second (rest args)))
	   	]
	   	(update-data (get-uri user db-name) data)
	  )
	  (println "Usage: lein run user db-name data_file")
	)
)
