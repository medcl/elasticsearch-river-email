Email River for Elasticsearch
===========================

An Email River Plugin for [Elasticsearch](http://www.elasticsearch.org/)


Versions
--------

<table>
	<thead>
		<tr>
			<td>Email River Plugin</td>
			<td>ElasticSearch</td>
		</tr>
	</thead>
	<tbody>
		<tr>
			<td>master (0.1.0)</td>
			<td>0.20.2</td>
		</tr>
	</tbody>
</table>


Creating a Email river
--------------------

We create first an index to store all the *emails* :

```sh 
$ curl -XPUT 'localhost:9200/google/'
```

We create the river with the following properties :

```sh
$ curl -XPUT 'localhost:9200/_river/google/_meta' -d '{
  "type": "email",
  "email": {
    "config" : [ {
    	"host": "pop.exmail.qq.com",
    	"port": 110,
    	"type":"pop3",
    	"username":"river@infinitbyte.com",
    	"password":"ail?sid=9UL",
    	"check_interval": 5000,
    	"skip_count": 1,
    	}
    ]
  },
  "index":{
    "index":"google",
    "type":"gmail"
  }
}'
```

We can check the indexed result now :


```sh
$ curl -XGET 'http://localhost:9200/google/_search?q=*''
```

To Do List
==========

* save attachments
