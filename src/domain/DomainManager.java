package domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSON;
import com.google.common.net.InternetDomainName;

import GUI.GUIMain;
import Tools.DomainComparator;
import burp.BurpExtender;
import burp.GrepUtils;
import domain.target.TargetEntry;
import domain.target.TargetTableModel;

/*
 *注意，所有直接对DomainObject中数据的修改，都不会触发该tableChanged监听器。
 *1、除非操作的逻辑中包含了firexxxx来主动通知监听器。比如DomainPanel.domainTableModel.fireTableChanged(null);
 *2、或者主动调用显示和保存的函数直接完成，不经过监听器。
	//GUI.getDomainPanel().showToDomainUI();
	//DomainPanel.autoSave();
 */
public class DomainManager {
	public String summary = "";
	public boolean autoAddRelatedToRoot = false;

	private CopyOnWriteArraySet<String> subDomainSet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> similarDomainSet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> relatedDomainSet = new CopyOnWriteArraySet<String>();
	//private Set<String> IsTargetButUselessDomainSet = new CopyOnWriteArraySet<String>();
	//有效(能解析IP)但无用的域名，比如JD的网店域名、首页域名等对信息收集、聚合网段、目标界定有用，但是本身几乎不可能有漏洞的资产。

	private CopyOnWriteArraySet<String> NotTargetIPSet = new CopyOnWriteArraySet<String>();
	//存储域名解析到的CDN或云服务的IP。这类IP在做网段汇算时，应当被排除在外。

	//private ConcurrentHashMap<String, Integer> unkownDomainMap = new ConcurrentHashMap<String, Integer>();//记录域名和解析失败的次数，大于五次就从子域名中删除。
	private CopyOnWriteArraySet<String> EmailSet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> similarEmailSet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> PackageNameSet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> SpecialPortTargets = new CopyOnWriteArraySet<String>();//用于存放指定了特殊端口的目标

	private CopyOnWriteArraySet<String> IPSetOfSubnet = new CopyOnWriteArraySet<String>();
	private CopyOnWriteArraySet<String> IPSetOfCert = new CopyOnWriteArraySet<String>();
	private GUIMain guiMain;

	//private Set<String> newAndNotGetTitleDomainSet = new CopyOnWriteArraySet<String>();

	public static int SUB_DOMAIN = 0;
	public static int SIMILAR_DOMAIN = 1;
	public static int IP_ADDRESS = 2;//属于某个目标网段，是确定的IP地址
	public static int PACKAGE_NAME = 3;
	public static int TLD_DOMAIN = 4; //比如baidu.net是baidu.com的TLD domain。xxx.baiu.net和xxx.baidu.com也是
	public static int NEED_CONFIRM_IP = 5; //根据目标无法判断的类型。
	public static int USELESS = -1;

	public static int CERTAIN_EMAIL = 10;
	public static int SIMILAR_EMAIL = 11;
	//public static int BLACKLIST = -2;

	public DomainManager() {
		//to resolve "default constructor not found" error
	}

	public DomainManager(GUIMain guiMain) {
		this.guiMain = guiMain;
	}


	public GUIMain getGuiMain() {
		return guiMain;
	}

	public void setGuiMain(GUIMain guiMain) {
		this.guiMain = guiMain;
	}

	public boolean isAutoAddRelatedToRoot() {
		return autoAddRelatedToRoot;
	}

	public void setAutoAddRelatedToRoot(boolean autoAddRelatedToRoot) {
		this.autoAddRelatedToRoot = autoAddRelatedToRoot;
	}

	/**
	 * 
	 * @return
	 */
	public Set<String> getSubDomainSet() {
		return subDomainSet;
	}

	public Set<String> getSimilarDomainSet() {
		return similarDomainSet;
	}

	public Set<String> getRelatedDomainSet() {
		return relatedDomainSet;
	}

	public Set<String> getNotTargetIPSet() {
		return NotTargetIPSet;
	}

	public Set<String> getEmailSet() {
		return EmailSet;
	}

	public Set<String> getSimilarEmailSet() {
		return similarEmailSet;
	}

	public Set<String> getPackageNameSet() {
		return PackageNameSet;
	}

	public Set<String> getSpecialPortTargets() {
		return SpecialPortTargets;
	}

	public Set<String> getIPSetOfSubnet() {
		return IPSetOfSubnet;
	}

	public Set<String> getIPSetOfCert() {
		return IPSetOfCert;
	}

	/**
	 * 上面没有提供setter函数，由此代替
	 * @param type
	 * @param content
	 */
	public void fillContentByType(TextAreaType type,Set<String> content) {
		switch (type) {
		case SubDomain:
			//dao.createOrUpdateByType(content, type);
			getSubDomainSet().clear();
			getSubDomainSet().addAll(content);
			break;
		case RelatedDomain:
			getRelatedDomainSet().clear();
			getRelatedDomainSet().addAll(content);
			break;
		case SimilarDomain:
			getSimilarDomainSet().clear();
			getSimilarDomainSet().addAll(content);
			break;
		case Email:
			getEmailSet().clear();
			getEmailSet().addAll(content);
			break;
		case SimilarEmail:
			getSimilarEmailSet().clear();
			getSimilarEmailSet().addAll(content);
			break;
		case IPSetOfSubnet:
			getIPSetOfSubnet().clear();
			getIPSetOfSubnet().addAll(content);
			break;
		case IPSetOfCert:
			getIPSetOfCert().clear();
			getIPSetOfCert().addAll(content);
			break;
		case SpecialPortTarget:
			getSpecialPortTargets().clear();
			getSpecialPortTargets().addAll(content);
			break;
		case PackageName:
			getPackageNameSet().clear();
			getPackageNameSet().addAll(content);
			break;
		case BlackIP:
			getNotTargetIPSet().clear();
			getNotTargetIPSet().addAll(content);
			break;
		}
	}

	public TargetTableModel fetchTargetModel() {
		return guiMain.getDomainPanel().getTargetTable().getTargetModel();
	}

	public String getSummary() {
		String filename = "unknown";
		if (guiMain.getCurrentDBFile() != null) {
			filename = guiMain.getCurrentDBFile().getName();
		}
		int targetSum = 0;
		try {
			targetSum += fetchTargetModel().getRowCount();
		} catch (Exception e) {
			//e.printStackTrace();
		}
		String tmpsummary = String.format("  FileName:%s  Root-domain:%s  Related-domain:%s  Sub-domain:%s  Similar-domain:%s  Email:%s "
				+ "IPOfSubnet:%s IPOfCert:%s ^_^",
				filename, targetSum, relatedDomainSet.size(), subDomainSet.size(), similarDomainSet.size(), EmailSet.size(),
				IPSetOfSubnet.size(),IPSetOfCert.size());
		return tmpsummary;
	}

	public boolean isEmpty() {
		int sum = 0;
		try {
			sum += fetchTargetModel().getRowCount();
		} catch (Exception e) {
			//e.printStackTrace();
		}
		sum += relatedDomainSet.size();
		sum += subDomainSet.size();
		sum += similarDomainSet.size();
		sum += EmailSet.size();
		sum += similarEmailSet.size();
		return sum == 0;
	}

	public void setSummary(String Summary) {
		this.summary = Summary;

	}

	/**
	 * 判断对象是否有变化，作为写入数据库的依据
	 * @return
	 */
	public boolean isChanged(){
		String status = getSummary();
		if (!status.equals(summary) && !summary.equals("")){
			summary = getSummary();
			guiMain.getDomainPanel().getLblSummary().setText(summary);
			return true;
		}else {
			return false;
		}
	}


	////////////////ser and deser///////////

	public String ToJson() {
		return JSON.toJSONString(this);
		//https://blog.csdn.net/qq_27093465/article/details/73277291
		//return new Gson().toJson(this);
	}

	//使用fastjson，否则之前的域名数据会获取失败！
	public static DomainManager FromJson(String instanceString) {
		return JSON.parseObject(instanceString, DomainManager.class);
		//return new Gson().fromJson(instanceString, DomainManager.class);
	}

	// below methods is self-defined, function name start with "fetch" to void fastjson parser error

	public String fetchRelatedDomains() {
		return String.join(System.lineSeparator(), relatedDomainSet);
	}

	public String fetchSimilarDomains() {
		return String.join(System.lineSeparator(), similarDomainSet);
	}

	public String fetchSubDomains() {
		List<String> tmplist = new ArrayList<>(subDomainSet);
		tmplist.sort(new DomainComparator());
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchSpecialPortTargets() {
		List<String> tmplist = new ArrayList<>(SpecialPortTargets);
		tmplist.sort(new DomainComparator());
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchSubDomainsOf(String rootDomain) {
		List<String> tmplist = new ArrayList<>();
		if (fetchTargetModel().assetType(rootDomain) == DomainManager.SUB_DOMAIN) {//判断是否有效rootDomain
			if (!rootDomain.startsWith(".")) {
				rootDomain = "." + rootDomain;
			}
			for (String item : subDomainSet) {
				if (item.endsWith(rootDomain)) {
					tmplist.add(item);
				}
			}
			Collections.sort(tmplist);
			return String.join(System.lineSeparator(), tmplist);
		}
		return "";
	}

	public String fetchEmails() {
		List<String> tmplist = new ArrayList<>(EmailSet);
		Collections.sort(tmplist);
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchPackageNames() {
		return String.join(System.lineSeparator(), PackageNameSet);
	}

	public String fetchSimilarEmails() {
		List<String> tmplist = new ArrayList<>(similarEmailSet);
		Collections.sort(tmplist);
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchIPSetOfSubnet() {
		List<String> tmplist = new ArrayList<>(IPSetOfSubnet);
		Collections.sort(tmplist);
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchIPSetOfCert() {
		List<String> tmplist = new ArrayList<>(IPSetOfCert);
		Collections.sort(tmplist);
		return String.join(System.lineSeparator(), tmplist);
	}

	public String fetchIPBlackList() {
		List<String> tmplist = new ArrayList<>(NotTargetIPSet);
		Collections.sort(tmplist);
		return String.join(System.lineSeparator(), tmplist);
	}

	/**
	 *
	 * 用于判断站点是否是我们的目标范围，原理是根据证书的所有域名中，是否有域名包含了关键词。
	 * 为了避免漏掉有效目标，只有完全确定非目标的才排除！！！
	 */
	public boolean isTargetByCertInfo(Set<String> certDomains) {
		if (certDomains.isEmpty() || certDomains == null) {//不能判断的，还是暂时认为是在目标中的。
			return true;
		}
		for (String domain : certDomains) {
			int type = fetchTargetModel().assetType(domain);
			if (type == DomainManager.SUB_DOMAIN || type == DomainManager.TLD_DOMAIN) {
				return true;
			}
		}
		return false;
	}

	public void addToTargetAndSubDomain(String enteredRootDomain, boolean autoSub) {
		if (enteredRootDomain == null) return;
		if (guiMain.getDomainPanel().fetchTargetModel().addRowIfValid(new TargetEntry(enteredRootDomain, autoSub))) {
			subDomainSet.add(enteredRootDomain);
		};
	}

	public void addTLDToTargetAndSubDomain(String enteredRootDomain) {
		if (enteredRootDomain == null) return;
		String tldDomainToAdd  = guiMain.getDomainPanel().fetchTargetModel().getTLDDomainToAdd(enteredRootDomain);
		TargetEntry tmp = new TargetEntry(tldDomainToAdd, false);
		guiMain.getDomainPanel().fetchTargetModel().addRowIfValid(tmp);
		if (guiMain.getDomainPanel().fetchTargetModel().addRowIfValid(tmp)) {
			subDomainSet.add(enteredRootDomain);
		};
	}

	public void addIfValid(Set<String> domains) {
		for (String domain:domains) {
			addIfValid(domain);
		}
	}
	/**
	 * 根据已有配置进行添加，不是强行直接添加
	 *
	 * @param domain
	 * @return boolean 执行了添加返回true，没有执行添加返回false。
	 */
	public boolean addIfValid(String domain) {
		Set<String> domains = GrepUtils.grepDomain(domain);//这样以支持domain:port形式的资产
		List<String> ips = GrepUtils.grepIPAndPort(domain);
		if (domains.size() !=0 ){
			domain = new ArrayList<String>(domains).get(0);
		} else if(ips.size() !=0){
			domain = ips.get(0);
		}else{
			return false;
		}

		int type = fetchTargetModel().assetType(domain);

		if (type !=DomainManager.USELESS && type!= DomainManager.NEED_CONFIRM_IP){
			BurpExtender.getStdout().println("Target Asset Found: "+domain);
		}
		if (type == DomainManager.TLD_DOMAIN) {
			//应当先做TLD域名的添加，这样可以丰富Root域名，避免数据损失遗漏
			//这里的rootDomain不一定是topPrivate。比如 shopeepay.shopee.sg 和shopeepay.shopee.io
			//这个时候就不能自动取topPrivate。
			addTLDToTargetAndSubDomain(domain);
			return true;
		} else if (type == DomainManager.SUB_DOMAIN) {//包含手动添加的IP
			subDomainSet.add(domain);//子域名可能来自相关域名和相似域名。
			//gettitle的逻辑中默认会请求80、443，所以无需再添加不包含端口的记录
			return true;
		} else if (type == DomainManager.SIMILAR_DOMAIN) {
			similarDomainSet.add(domain);
			return true;
		} else if (type == DomainManager.PACKAGE_NAME) {
			PackageNameSet.add(domain);
			return true;
		} else if (type == DomainManager.IP_ADDRESS){
			IPSetOfSubnet.add(domain);
			return true;
			//不再直接添加收集到但是无法确认所属关系的IP，误报太高
			//		} else if (type == DomainManager.NEED_CONFIRM_IP){
			//			SpecialPortTargets.add(domain);
			//			return true;
		}else{
			return false;
		}//Email的没有处理
	}


	public void addIfValidEmail(Set<String> emails) {
		for (String email:emails) {
			addIfValidEmail(email);
		}
	}


	/**
	 * 根据已有配置进行添加，不是强行直接添加
	 *
	 * @param email
	 * @return boolean 执行了添加返回true，没有执行添加返回false。
	 */
	public boolean addIfValidEmail(String email) {
		if (email == null) return false;

		int type = fetchTargetModel().emailType(email);

		if (type == DomainManager.CERTAIN_EMAIL) {
			EmailSet.add(email);
			return true;
		} else if (type == DomainManager.SIMILAR_EMAIL) {//包含手动添加的IP
			similarEmailSet.add(email);//子域名可能来自相关域名和相似域名。
			return true;
		}else{
			return false;
		}
	}

	/**
	 * 根据规则重新过一遍所有的数据
	 * 
	 * 1、子域名、相似域名、子网IP、邮箱、相似邮箱、package name 都很好处理。与目标有明确的关联关系
	 * 2、相关域名、IPofCert都是根据证书信息进行关联的，有点不好处理。
	 * 相关域名只能在原始数据的基础上做排除，已在子域名中的，就无需存储在相关域名中了
	 * IPofCert就只能先不做处理，除非能记录器证书信息，或者从title中查询其信息进行判断。
	 * IPofCert也可以排除已在IPSetOfSubnet中的部分
	 *
	 * 假如用户手动编辑了target。那么就需要依靠刷新的操作来更新数据。所以单纯靠添加时的处理逻辑是不够的。
	 * 
	 * 新增的刷新逻辑还可以简化，子域名等无需再次分析。
	 */
	public void freshBaseRule() {
		guiMain.getDomainPanel().backupDB("before refresh");
		BurpExtender.getStdout().println("before refresh--> "+getSummary());

		Set<String> tmpDomains = new HashSet<>();
		//tmpDomains.addAll(relatedDomainSet);

		tmpDomains.addAll(subDomainSet);
		tmpDomains.addAll(similarDomainSet);
		tmpDomains.addAll(PackageNameSet);
		tmpDomains.addAll(IPSetOfSubnet);
		tmpDomains.addAll(relatedDomainSet);

		subDomainSet.clear();
		similarDomainSet.clear();
		PackageNameSet.clear();
		IPSetOfSubnet.clear();

		addIfValid(tmpDomains);

		//相关域名、IPofCert都是根据证书信息进行关联的，目前就只做排除操作。
		relatedDomainSet.removeAll(subDomainSet);
		IPSetOfCert.removeAll(IPSetOfSubnet);

		//处理Email
		HashSet<String > tmpEmalis = new HashSet<>();

		guiMain.getDomainPanel().collectEmailFromIssue();
		tmpEmalis.addAll(EmailSet);
		tmpEmalis.addAll(similarEmailSet);

		EmailSet.clear();
		similarEmailSet.clear();
		guiMain.getDomainPanel().getDomainResult().addIfValidEmail(tmpEmalis);

		BurpExtender.getStdout().println("after refresh--> "+getSummary());
	}

	/**
	 * 当UI界面操作时，会被调用
	 * 还有当自动化搜集时，会调用
	 */
	public void relatedToRoot() {
		if (this.autoAddRelatedToRoot) {
			if (relatedDomainSet.size() >0){
				HashSet<String> tmpSet = new HashSet<String>(relatedDomainSet);
				for (String relatedDomain : tmpSet) {
					try{
						//避免直接引用relatedDomainSet进行循环，由于TargetEntry中有删除操作，会导致'java.util.ConcurrentModificationException'异常
						if (relatedDomain != null && relatedDomain.contains(".")) {
							if (fetchTargetModel().isBlack(relatedDomain)) {
								continue;
							}
							addToTargetAndSubDomain(relatedDomain, true);
							//底层调用了addRow，每调用一次都会触发数据库写操作。重构后是新增一条记录
						} else {
							System.out.println("error related domain : " + relatedDomain);
						}
					}catch (Exception e){
						BurpExtender.getStderr().println(relatedDomain);
						e.printStackTrace(BurpExtender.getStderr());
					}
				}
			}
		}
	}


	/**
	 * CopyOnWriteArraySet 用iterator的remove反而会出错
	 */
	public void removeMd5Domain() {
		for (String item:subDomainSet) {
			String md5 = isMd5Domain(item);//md5的值加上一个点
			if (md5.length() == 33) {
				subDomainSet.remove(item);
				subDomainSet.add(item.replace(md5, ""));
			}
		}

		for (String item:similarDomainSet) {
			String md5 = isMd5Domain(item);//md5的值加上一个点
			if (md5.length() == 33) {
				similarDomainSet.remove(item);
				similarDomainSet.add(item.replace(md5, ""));
			}
		}
	}

	public static String isMd5Domain(String domain) {
		Pattern pattern = Pattern.compile("[a-fA-F0-9]{32}\\.");
		Matcher matcher = pattern.matcher(domain);
		if (matcher.find()) {//多次查找
			String md5 = matcher.group();
			return md5;
		}
		return "";
	}

	public static void test1(String enteredRootDomain) {
		enteredRootDomain = InternetDomainName.from(enteredRootDomain).topPrivateDomain().toString();
		System.out.println(enteredRootDomain);
	}
	
	/**
	 *  CopyOnWriteArraySet 用iterator的remove反而会出错
	 */
	public static void test2() {
		CopyOnWriteArraySet<String> subDomainSet = new CopyOnWriteArraySet<String>();
		subDomainSet.add("e53cf27d3dad22ae36aff189d90f0fbf.aaa.com");
		
		for (String item:subDomainSet) {
			String md5 = isMd5Domain(item);//md5的值加上一个点
			if (md5.length() == 33) {
				subDomainSet.remove(item);
				subDomainSet.add(item.replace(md5, ""));
			}
		}
		System.out.println(String.join(",", subDomainSet));
	}

	public static void main(String args[]) {
		//test1("order-admin.test.shopee.in");
	}

}
