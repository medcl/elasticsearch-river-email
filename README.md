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
			<td>0.2.0</td>
			<td>0.90.2</td>
		</tr>
		<tr>
            <td>master (0.3.0)</td>
            <td>1.0.0</td>
        </tr>
	</tbody>
</table>

Changes
--------------------
in 0.2.0,you need a weedfs(http://code.google.com/p/weed-fs) server to save attachments.


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


```sh
{
    "_index": "google",
    "_type": "gmail",
    "_id": "8zynxKKTS9efn7J1YZalpw",
    "_version": 1,
    "exists": true,
    "_source": {
        "subject": "images",
        "sent_date": "2014-02-15 21:07:26 星期六",
        "from_mail": [
            "river@infinitbyte.com"
        ],
        "reply_to_mail": [
            "river@infinitbyte.com"
        ],
        "from": "river <river@infinitbyte.com>",
        "to": "river <river@infinitbyte.com>",
        "content_type": "multipart/mixed",
        "summary": "3images3images",
        "content": "3images<div>3images</div>",
        "priority": "Normal",
        "need_receipt": false,
        "size": "81kb",
        "contain_attachment": true,
        "timestamp": 1392474129786,
        "via_river": "google",
        "attachments": [
            {
                "file_name": "88a5fbf2b2119313e8c2f93965380cd793238d98.jpg",
                "file_size": 33854,
                "file_id": "2,0668b35d099ceb",
                "summary": null
            },
            {
                "file_name": "EKVBYGQ8OY0Y.png",
                "file_size": 9082,
                "file_id": "5,0668b4f17a3ed0",
                "summary": null
            },
            {
                "file_name": "icon.jpg",
                "file_size": 17269,
                "file_id": "3,0668b58384f737",
                "summary": null
            }
        ]
    }
}
```

Tips
--------------------
you can access the attachments by visit: http://weedfs_server:port/file_id,for example:
http://localhost:8080/3,0668b58384f737

WeedFs Related Settings
--------------------
weedfs_host:"127.0.0.1"
weedfs_master_port:9333
weedfs_volume_port:8080