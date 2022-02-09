#!/usr/bin/perl

use warnings;
use strict;

use File::Path; # Provides mkdir -p like functionality

#
# RPKI Stats Collector
#
# For configuration see inline below, before the first 'sub'..

#
# Specify the base dir for this script. The script expects a subdir called
# 'tal' here where it will process all files called '*.tal' as Trust Anchors
my $stats_script_dir="/home/app-admin/cert-stats";

# Specify the full path to the validator
my $validator_bin=$stats_script_dir."/validator/bin/certification-validator";

#
# Specify the base directory where this script will archive the
# validation output and summary.
#
# The script will store results like this:
#
#  /path/to/base/data/dir
#           /example.tal
#                 stats-summary.txt
#                 /20110131   (date)
#                     validator.log
#                     roas.csv
#                     out/... (normal validator out work-directory)
#                 /... (more dates)
#           /... (more TALs)
#
my $base_data_dir="/ncc/archive/certstats";

# The script will store the summary output for each tal in a separate file
# and sync it over to whelk. Do not use trailing slashes.. they are added by the code..
my $summaries_dir = $base_data_dir."/summaries";
my $rsync_target = "whelk:/cert/content/static/statistics";


sub compose_directory_path_for_tal_and_date_archiving($) {
   my $tal_dir = shift;
   my $year = `date +%Y`; chomp($year);
   my $mnth = `date +%m`; chomp($mnth);
   my $day = `date +%d`; chomp($day);
   return $tal_dir."/".$year."/".$mnth."/".$day;
}

# Process this TAL
sub process_tal($) {
  my $tal = shift;

  my $tal_stats_dir = $base_data_dir."/".$tal;
  my $todays_stat_dir = compose_directory_path_for_tal_and_date_archiving($tal_stats_dir);
  mkpath($todays_stat_dir);

  my $validator_log_file = "$todays_stat_dir/validator.log";
  my $tal_stats_file = "$summaries_dir/$tal.txt";

  system ("$validator_bin -t $tal -o $todays_stat_dir/out -r $todays_stat_dir/roas.csv >$validator_log_file 2>&1");
  system ("tail -1 $validator_log_file >> $tal_stats_file");
}


sub main_loop() {

   chdir "$stats_script_dir/tal";
   my @tal_files = <*.tal>;
   foreach my $tal (@tal_files) {
     process_tal($tal);
   }

   system("rsync -q $summaries_dir/*.txt $rsync_target/");
}


main_loop();
