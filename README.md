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
			<td>0.1.0</td>
			<td>0.20.2</td>
		</tr>
		<tr>
			<td>master (0.2.0)</td>
			<td>0.90.2</td>
		</tr>
	</tbody>
</table>

Changes:
in 0.2.0,you need a weedfs server to save attachemts.


Creating a Email river
--------------------

First create an index to store all the *emails* :

```sh 
$ curl -XPUT http://localhost:9200/google/
```

Then create the river with the following properties :

```sh
$ curl -XPUT http://localhost:9200/_river/google/_meta -d '{
                                                               "type": "email",
                                                               "email": {
                                                                   "config": [
                                                                       {
                                                                           "host": "pop.exmail.qq.com",
                                                                           "port": 110,
                                                                           "type": "pop3",
                                                                           "username": "river@infinitbyte.com",
                                                                           "password": "ail?sid=9UL",
                                                                           "check_interval": 5000,
                                                                           "skip_count": 1
                                                                       }
                                                                   ]
                                                               },
                                                               "index": {
                                                                   "index": "google",
                                                                   "type": "gmail"
                                                               }
                                                           }'
```

And we can check the indexed result now :


```sh
$ curl -XGET http://localhost:9200/google/_search?q=*
```


