/*
 * Cloud9: A Hadoop toolkit for working with big data
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.umd.honghongie;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import tl.lin.data.pair.PairOfObjectFloat;
import tl.lin.data.queue.TopScoredObjects;
import tl.lin.data.array.ArrayListOfInts;
import tl.lin.data.array.ArrayListOfFloatsWritable;

public class ExtractTopPersonalizedPageRankNodes extends Configured implements Tool {
  private static final Logger LOG = Logger.getLogger(ExtractTopPersonalizedPageRankNodes.class);

  private static class MyMapper extends
      Mapper<IntWritable, PageRankNode, IntWritable, FloatWritable> {
    private TopScoredObjects<Integer> queue;
    private int times;

    @Override
    public void setup(Context context) throws IOException {
      int k = context.getConfiguration().getInt("n", 100);
      times = context.getConfiguration().getInt("times", 0);  //****
      queue = new TopScoredObjects<Integer>(k);
    }

    @Override
    public void map(IntWritable nid, PageRankNode node, Context context) throws IOException,
        InterruptedException {
      ArrayListOfFloatsWritable pagerankvalues = node.getPageRank();
      queue.add(node.getNodeId(), pagerankvalues.get(times));
    }

    @Override
    public void cleanup(Context context) throws IOException, InterruptedException {
      IntWritable key = new IntWritable();
      FloatWritable value = new FloatWritable();

      for (PairOfObjectFloat<Integer> pair : queue.extractAll()) {
        key.set(pair.getLeftElement());
        value.set(pair.getRightElement());
        context.write(key, value);
      }
    }
  }

  private static class MyReducer extends
      Reducer<IntWritable, FloatWritable, IntWritable, FloatWritable> {
    private static TopScoredObjects<Integer> queue;
    private int sourceid; //***

    @Override
    public void setup(Context context) throws IOException {
      int k = context.getConfiguration().getInt("n", 100);
      queue = new TopScoredObjects<Integer>(k);
      sourceid = context.getConfiguration().getInt("source", 0); //***
 
    }

    @Override
    public void reduce(IntWritable nid, Iterable<FloatWritable> iterable, Context context)
        throws IOException {
      Iterator<FloatWritable> iter = iterable.iterator();
      float pagerank = (float)StrictMath.exp(iter.next().get()); // transform to normal pagerank value
      queue.add(nid.get(), pagerank);

      // Shouldn't happen. Throw an exception.
      if (iter.hasNext()) {
        throw new RuntimeException();
      }
    }

    @Override
    public void cleanup(Context context) throws IOException, InterruptedException {
      IntWritable key = new IntWritable();
      FloatWritable value = new FloatWritable();

      System.out.println("Source: "+ sourceid);

      for (PairOfObjectFloat<Integer> pair : queue.extractAll()) {
        key.set(pair.getLeftElement());
        value.set(pair.getRightElement());
        context.write(key, value);

        float pagerank = pair.getRightElement();
        int thenodeid = pair.getLeftElement();
        String ss = String.format("%.5f %d", pagerank, thenodeid);
        System.out.println(ss);

      }
    }
  }

  public ExtractTopPersonalizedPageRankNodes() {
  }

  private static final String INPUT = "input";
  private static final String OUTPUT = "output";
  private static final String TOP = "top";
  private static final String SOURCE = "sources";

  /**
   * Runs this tool.
   */
  @SuppressWarnings({ "static-access" })
  public int run(String[] args) throws Exception {
    Options options = new Options();

    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("input path").create(INPUT));
  //  options.addOption(OptionBuilder.withArgName("path").hasArg()
  //      .withDescription("output path").create(OUTPUT));
    options.addOption(OptionBuilder.withArgName("num").hasArg()
        .withDescription("top n").create(TOP));
    //******
    options.addOption(OptionBuilder.withArgName("node").hasArg()
        .withDescription("source nodes").create(SOURCE));

    CommandLine cmdline;
    CommandLineParser parser = new GnuParser();

    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      return -1;
    }

    if (!cmdline.hasOption(INPUT) || //!cmdline.hasOption(OUTPUT) || 
        !cmdline.hasOption(TOP) || !cmdline.hasOption(SOURCE)) //source node
    {
      System.out.println("args: " + Arrays.toString(args));
      HelpFormatter formatter = new HelpFormatter();
      formatter.setWidth(120);
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      return -1;
    }

    String inputPath = cmdline.getOptionValue(INPUT);
    
    String outputPath = "TestOutput"; //*********????

    int n = Integer.parseInt(cmdline.getOptionValue(TOP));
    //*******
    String nodeid = cmdline.getOptionValue(SOURCE);

    ArrayListOfInts sourceids = new ArrayListOfInts();
    String ss[] = nodeid.split(",");
    for (int i = 0; i < ss.length; i++){
      sourceids.add(Integer.parseInt(ss[i]));
    }

    LOG.info("Tool name: " + ExtractTopPersonalizedPageRankNodes.class.getSimpleName());
    LOG.info(" - input: " + inputPath);
    LOG.info(" - output: " + outputPath);
    LOG.info(" - top: " + n);
    LOG.info(" - source node: " + nodeid);

    for (int i = 0; i < sourceids.size(); i++){
      int source = sourceids.get(i);
      iterateSort(i, source, n, inputPath, outputPath);  //times, sourcenode, top n
    }

    return 0;
  }


    //run each iteration
    private void iterateSort(int i, int source, int n, String input, String output) throws Exception {

      Configuration conf = getConf();
      conf.setInt("mapred.min.split.size", 1024 * 1024 * 1024);
      conf.setInt("n", n);
      conf.setInt("times", i);
      conf.setInt("source", source);

      Job job = Job.getInstance(conf);
      job.setJobName(ExtractTopPersonalizedPageRankNodes.class.getName() + ":" + input);
      job.setJarByClass(ExtractTopPersonalizedPageRankNodes.class);

      job.setNumReduceTasks(1);

      FileInputFormat.addInputPath(job, new Path(input));
      FileOutputFormat.setOutputPath(job, new Path(output + Integer.toString(i)));

      job.setInputFormatClass(SequenceFileInputFormat.class);
      job.setOutputFormatClass(TextOutputFormat.class);

      job.setMapOutputKeyClass(IntWritable.class);
      job.setMapOutputValueClass(FloatWritable.class);

      job.setOutputKeyClass(IntWritable.class);
      job.setOutputValueClass(FloatWritable.class);

      job.setMapperClass(MyMapper.class);
      job.setReducerClass(MyReducer.class);

      // Delete the output directory if it exists already.
      FileSystem.get(conf).delete(new Path(output + Integer.toString(i)), true);

      long startTime = System.currentTimeMillis();
      job.waitForCompletion(true);
      System.out.println("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");

    }

  /**
   * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
   */
  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new ExtractTopPersonalizedPageRankNodes(), args);
    System.exit(res);
  }
}
