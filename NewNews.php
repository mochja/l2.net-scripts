<?php
$mysql = mysql_connect("localhost", "root", "");
	if(!$mysql)
		die();
	$mysql_db = mysql_select_db("l2_thirfiel");
	if(!$mysql_db)
		die();

$online      = 0;
$banned		 = 0;
$online_gms  = array();  

$accounts = array();
$clans    = array();
$gms      = 0;

$query = mysql_query("SELECT account_name, char_name, accesslevel, online, clanid FROM characters ORDER BY char_name ASC;");

while ($row = mysql_fetch_array($query))
{        	
	if (!in_array($row["clanid"], $clans))
		$clans[] = $row["clanid"];
	
	if (!in_array($row["account_name"], $accounts))
		$accounts[] = $row["account_name"];
		
	if ($row["online"] > 0)
	{
		if ($row["accesslevel"] > 0)
			$online_gms[] = $row["char_name"];
		else			
			$online++;    
	}
	else
	{	
		if ($row["accesslevel"] > 0)
			$gms++;
		else if ($row["accesslevel"] < 0)
			$banned++; 
		else
			$total_chars++;
	} 			
}
$gmlist = "";
foreach $online_gms as $gm
{	
	$gmlist .= $gm . ", ";
}
echo mysql_error();

echo "<tr><td width='100%'>Online:</td><td>".$online."</td></tr>
<tr><td width='100%'>Počet GM:</td><td>".($gms + count($online_gms))."</td></tr>
<tr><td width='100%'>Počet účtů:</td><td>".count($accounts)."</td></tr>
<tr><td width='100%'>Počet postav:</td><td>".($total_chars + $online)."</td></tr>
<tr><td width='100%'>Clany:</td><td>".count($clans)."</td></tr>
<tr><td width='100%'></td><td></td></tr>
<tr><td width='100%'>Zabanované ACC:</td><td>".$banned."</td></tr>
<tr><td width='100%'></td><td></td></tr>
<tr><td width='100%'>Online GM: <br>".$gmlist."</td></tr>";
