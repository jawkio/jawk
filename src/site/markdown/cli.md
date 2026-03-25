# Jawk CLI

<!-- MACRO{toc|fromDepth=2|toDepth=3|id=toc} -->

## Getting Started

* Download [jawk-${project.version}-standalone.jar](https://github.com/metricshub/Jawk/releases/download/v${project.version}/jawk-${project.version}-standalone.jar) from the [latest release](https://github.com/metricshub/Jawk/releases)
* Make sure to have Java installed on your system ([download](https://adoptium.net/))
* Execute `jawk-${project.version}-standalone.jar` just like the "traditional" AWK:

    ```shell-session
    $ java -jar jawk-${project.version}-standalone.jar <command-line-arguments>
    ```

## Examples

### Processing of *stdin*

```shell-session
$ echo "hello world" | java -jar jawk-${project.version}-standalone.jar '{print $2 ", " $1 "!"}'

world, hello!
```

Execute on Windows (beware of double-double quotes!):

```shell-session
C:\> echo hello world | java -jar jawk-${project.version}-standalone.jar "{print $2 "", "" $1 ""!""}"

world, hello!
```

### Classic processing of an input file

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -F : '{print $1 "," $6}' /etc/passwd

root,/root
daemon,/usr/sbin
bin,/bin
sys,/dev
sync,/bin
games,/usr/games
man,/var/cache/man
lp,/var/spool/lpd
mail,/var/mail
```

### Execute a script file

**example.awk**:

```awk
BEGIN {
  totalUsed = 0
  totalAvailable = 0
}
$6 !~ "wsl" && $3 ~ "[0-9]+" {
  totalUsed += $3
  totalAvailable += $4
}
END {
  printf "Total Used: %.1f GB\n", totalUsed / 1048576
  printf "Total Available: %.1f GB\n", totalAvailable / 1048576
}
```

```shell-session
$ df -kP | java -jar jawk-${project.version}-standalone.jar -f example.awk

Total Used: 559.8 GB
Total Available: 2048.0 GB
```

### Matrix in your terminal (Linux)

```shell-session
$ while :;do echo $LINES $COLUMNS $(( $RANDOM % $COLUMNS)) $(printf "\U$(($RANDOM % 500))");sleep 0.05;done|java -jar jawk-${project.version}-standalone.jar '{a[$3]=0;for (x in a){o=a[x];a[x]=a[x]+1;printf "\033[%s;%sH\033[2;32m%s",o,x,$4;printf "\033[%s;%sH\033[1;37m%s\033[0;0H",a[x],x,$4;if (a[x]>=$1){a[x]=0;}}}'
```

## Detailed Options

To view the command line argument usage summary, execute:

```shell-session
$ java -jar jawk-${project.version}-standalone.jar -?
```

**Jawk** supports all of the standard AWK command line parameters:

* `-v <name=value>` - global variable assignments prior to the execution of the script.
* `-F <fs>` - input field separator assignment. This is equivalent to `FS="fs"` prior to its use (by getline or by input rules).
* `-f <filename>` - The script filename. If used, a script argument is not expected.

To enhance development and script execution over traditional AWK, **Jawk** also supports the following command-line parameter extensions:

* `-t` - Maintain all associated arrays in key-sorted order. This is implemented by using a TreeMap instead of a HashMap as the backing store for the associated array.
* `-K <filename>` - writes the tuples to `<filename>`, and then halts.
* `-L <filename>` - load previously compiled tuples from the specified file.
* `-l <extension>`/`--load <extension>` - load an extension by its registered name, simple class name or fully qualified class name.
* `--list-ext` - list available extensions and exit. The output contains the registered name followed by the implementing class and can be used as input for `-l`.

When using extension names that contain spaces, wrap them in quotes so the shell passes the value as a single argument (for example `-l "My Custom Extension"`).

You can rely on the JVM `-cp`/`-classpath` option to add directories or JARs containing extensions before launching `java -jar jawk-….jar`.

* `-o <filename>` - Override the default output filename for extended parameters -z and -Z.
* `-S`/`--sandbox` - Run Jawk in sandbox mode, disabling `system()`, redirections (`getline < file`, `print > file`, etc.), command pipelines, and loading dynamic extensions.
* `--dump-syntax` - Print abstract syntax tree. Code is not executed.
* `--dump-intermediate` - Print the intermediate code (tuples). Code is not executed.
* `-s`/`--no-optimize` - Skip tuple queue optimizations during compilation.
* `-r` - Allow IllegalFormatExceptions to be thrown when using the java.util.Formatter class for printf/sprintf. If the argument is not provided, the interpreter/compiled result catches IllegalFormatExceptions and silently returns a blank string in its place. If the argument is provided, the interpreter/compiled result will halt by throwing this runtime exception.
* `-h`/`-?` - Displays a usage screen. The screen contains a list of command-line arguments and what each does.

If `-f` is not provided, a script argument is expected here.

Finally, one or more of the following parameters are consumed by **Jawk** and provided to the script via the ARGV/ARGC variables. The script can add/remove to this array to modify the behavior of the interpreter/compiled result.

* `<filename>` - Uses this file as input to the script. If the filename is invalid, an error is produced on stderr, but Jawk has no direct way of notifying the script.
* `<name=value>` - Performs this assignment as a global variable assignment prior to the consumption of the next input file.

If the parameter contains an `=`, **Jawk** treats it like a variable assignment. Otherwise, it's a filename.

> **Note:** Parameters passed into the command-line which result in non-execution of the script (i.e., --dump-syntax, --dump-intermediate, -h, -? and -z) cause **Jawk** to ignore filename and name=value parameters._

**Jawk** parses command-line parameters via the [`Cli`](apidocs/org/metricshub/jawk/Cli.html) class.

If an invalid command-line parameter is provided, **Jawk** will throw an *IllegalArgumentException* and terminate execution.
