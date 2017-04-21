package com.amexio;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;

public class CCCMain {
	private static final String ALL = "ALL";
	private static final String CC07 = "CC-0007";
	private static final String CC09 = "CC-0009";
	private static final String CC10 = "CC-0010";
	private static final String CC46 = "CC-0046";
	private static final String CC81 = "CC-0081";
	private static String dirPath="";
	private static String newOwnername="";
	private static String newAclname="";
	private static String newAcldomain="";
	private static String suffixe="";
	
	/**
	 * @param args
	 */
	public static void main(final String[] args) {
		ArrayList<String> validOptions = new ArrayList<String>(Arrays.asList(ALL,CC07,CC09,CC10,CC46,CC81));
		info("Début de traitement");
		if (args.length<=0) {
			error("Paramètre manquant.");
			usage();
			System.exit(1);
		} else if (args.length>2) {
			error("Trop de paramètres.");
			usage();
			System.exit(1);			
		} else if (!validOptions.contains(args[0])) {
			error("Paramètre incorrect");
			usage();
			System.exit(1);						
		}
		suffixe = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		String cccRep = CCCUtils.getConfigVal(CCCConstants.REPO, CCCConstants.PROPFILE);
		String cccUName = CCCUtils.getConfigVal(CCCConstants.USERNAME, CCCConstants.PROPFILE);
		String cccUPass = CCCUtils.getConfigVal(CCCConstants.USERPASS, CCCConstants.PROPFILE);
		dirPath = CCCUtils.getConfigVal(CCCConstants.OUPUT, CCCConstants.PROPFILE);
		try {
			String pathToCreate=dirPath;
			while (pathToCreate.endsWith(File.separator)) {
				pathToCreate=pathToCreate.substring(0, pathToCreate.length()-1);
			}
			 if (pathToCreate.indexOf(File.separator)>0) {
				 CCCUtils.makeDir(pathToCreate.substring(0, pathToCreate.lastIndexOf(File.separator)) , pathToCreate.substring(pathToCreate.lastIndexOf(File.separator)));
			 } else {
				 CCCUtils.makeDir(pathToCreate , "");				 
			 }
			dirPath=pathToCreate+File.separator;
		} catch (Exception e1) {
			error("Erreur lors de la création du dossier de sortie : "+dirPath,e1);
			System.exit(1);
		}
		
		newOwnername = CCCUtils.getConfigVal(CCCConstants.DEFAULT_OWNER, CCCConstants.PROPFILE);
		newAclname = CCCUtils.getConfigVal(CCCConstants.DEFAULT_ACLNAME, CCCConstants.PROPFILE);
		newAcldomain = CCCUtils.getConfigVal(CCCConstants.DEFAULT_ACLDOMAIN, CCCConstants.PROPFILE);
		info("Ouverture de session Documentum...");
		IDfSessionManager sMgr=null;
		IDfSession dfSession =null;
		try {
			sMgr = CCCUtils.createSessionManager(cccRep, cccUName, cccUPass);
			dfSession = sMgr.getSession(cccRep);
		} catch (DfException e) {
			error("Erreur lors de la prise de session Documentum",e);
			System.exit(1);
		}
		info("\tOK");
		boolean errorArguments=false;
		try {
			for (int i = 0; i < args.length; i++) {
				String argu=args[i];
				if (argu.equalsIgnoreCase(ALL)) {
					corrigeAll(dfSession);
				} else if (argu.equalsIgnoreCase(CC07)) {
					corrige0007(dfSession);
				} else if (argu.equalsIgnoreCase(CC09)) {
					corrige0009(dfSession);
				} else if (argu.equalsIgnoreCase(CC10)) {
					corrige0010(dfSession);
				} else if (argu.equalsIgnoreCase(CC46)) {
					corrige0046(dfSession);
				} else if (argu.equalsIgnoreCase(CC81)) {
					corrige0081(dfSession);
				} else {
					error("paramètre incorrect : "+argu);
					errorArguments=true;
				}
			}

		} catch (DfException e) {
			System.exit(1);
		} catch (IOException e) {
			System.exit(1);
		} finally {
			if (dfSession!=null) {
				CCCUtils.closeSess(sMgr, dfSession);
			}
		}
		if (errorArguments) {
			usage();
			System.exit(1);
		}

	}


	/**
	 * Correction des erreurs CC-0007
	 * @param dfSession Session Dctm
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrige0007(IDfSession dfSession) throws DfException, IOException {
		info("Analyse des CC-0007");
		String dql="select distinct r_object_id,object_name,r_accessor_name,i_position from dm_acl where  r_accessor_name not in (select user_name from dm_user) and r_accessor_name not in ('dm_world', 'dm_owner','dm_group') order by r_object_id,i_position enable(row_based)";
		String scriptFileName=getScriptName(CC07,"api");
		try {
			IDfCollection coll=CCCUtils.executeQuery(dql, dfSession);
			String aclId=null;
			String accessor=null;
			String acl_name=null;
			int position=0;
			byte[] strBytes = null;
			final StringBuffer sBuf = new StringBuffer();
			FileOutputStream scriptFile= null;
			int compteur=0;
			while(coll.next()) {
				if (scriptFile==null) {
					scriptFile=new FileOutputStream(dirPath + scriptFileName);
				}
				aclId = coll.getString("r_object_id");
				position = -coll.getInt("i_position")-1;
				accessor=coll.getString("r_accessor_name");
				acl_name=coll.getString("object_name");
				String logMsg="correction pour supprimer "+accessor+" de l'acl "+acl_name+" ("+aclId+")";
				debug(logMsg);
				sBuf.append("# ").append(logMsg).append("\n")
				.append("fetch,c,"+aclId).append("\n")
				.append("remove,c,"+aclId+",r_accessor_name["+position+"]").append("\n")
				.append("remove,c,"+aclId+",r_accessor_permit["+position+"]").append("\n")
				.append("remove,c,"+aclId+",r_accessor_xpermit["+position+"]").append("\n")
				.append("remove,c,"+aclId+",r_is_group["+position+"]").append("\n")
				.append("save,c,"+aclId).append("\n");
				compteur++;
			}
			info("\tCC-0007 détecté(s) : "+compteur);
			if (compteur>0) {
				strBytes = sBuf.toString().getBytes();
				scriptFile.write(strBytes);
				scriptFile.close();
				info("\tLe script '"+dirPath+scriptFileName+"' a été créé. Merci de l'exécuter.");
			}
		} catch (DfException e) {
			error("Erreur lors de la génération du script de correction des erreurs CC-0007" ,e);
			throw e;
		} catch (FileNotFoundException e) {
			error("Erreur fichier de sortie non trouvé : "+dirPath + scriptFileName,e);
			throw e;
		} catch (IOException e) {
			error("Erreur lors de la création du fichier de sortie  : "+dirPath + scriptFileName,e);
			throw e;
		}
	}
	private static String getScriptName(String prefixe, String extension) {
		return prefixe+"_"+suffixe+"."+extension;
	}


	/**
	 * Correction des erreurs CC-0009 : l'utilisateur défini comme 'acl_domain' n'existe plus 
	 * @param dfSession Session Dctm
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrige0009(IDfSession dfSession) throws DfException, IOException {
		String errorMsg="";
		if (newAclname==null || newAclname.equals("")) {
			errorMsg="Le fichier properties ne contient pas de valeur pour "+CCCConstants.DEFAULT_ACLNAME+"\n";
		}
		if (newAcldomain==null || newAcldomain.equals("")) {
			errorMsg+="Le fichier properties ne contient pas de valeur pour "+CCCConstants.DEFAULT_ACLDOMAIN;
		}
		if (!errorMsg.equals("")) {
			error(errorMsg);
			throw new DfException(errorMsg);
		}
		info("Analyse des CC-0009");
		String dqlSlt="select r_object_id,object_name,acl_name,acl_domain from dm_sysobject (all) where acl_domain not in (select user_name from dm_user);";
		String dqlUpd="update dm_sysobject_s set acl_name = '{DEFAULT_ACL_NAME}', set acl_domain = '{DEFAULT_ACL_DOMAIN}' where r_object_id ='{ROID}';";
		String scriptFileName=getScriptName(CC09,"sql");
		try {
			IDfCollection coll=CCCUtils.executeQuery(dqlSlt, dfSession);
			String objectId=null;
			String acl_name=null;
			String acl_domain=null;
			String object_name=null;
			byte[] strBytes = null;
			final StringBuffer sBuf = new StringBuffer();
			FileOutputStream scriptFile= null;
			int compteur=0;
			while(coll.next()) {
				if (scriptFile==null) {
					scriptFile=new FileOutputStream(dirPath + scriptFileName);
				}
				objectId = coll.getString("r_object_id");
				acl_name=coll.getString("acl_name");
				acl_domain=coll.getString("acl_domain");
				object_name=coll.getString("object_name");
				String logMsg="correction pour changer l'acl_name/acl_domain de l'objet "+object_name+" ("+objectId+") : "+acl_name+"/"+acl_domain+" -> "+newAclname+"/"+newAcldomain;
				debug(logMsg);
				dqlUpd=dqlUpd.replace("{DEFAULT_ACL_NAME}", newAclname);
				dqlUpd=dqlUpd.replace("{DEFAULT_ACL_DOMAIN}", newAcldomain);
				dqlUpd=dqlUpd.replace("{ROID}", objectId);
				sBuf.append("# ").append(logMsg).append("\n")
				.append(dqlUpd).append("\n");
				compteur++;
			}
			info("\tCC-0009 détecté(s) : "+compteur);
			if (compteur>0) {
				strBytes = sBuf.toString().getBytes();
				scriptFile.write(strBytes);
				scriptFile.close();
				info("\tLe script '"+dirPath+scriptFileName+"' a été créé. Merci de l'exécuter.");
			}
		} catch (DfException e) {
			error("Erreur lors de la génération du script de correction des erreurs CC-0009" ,e);
			throw e;
		} catch (FileNotFoundException e) {
			error("Erreur fichier de sortie non trouvé : "+dirPath + scriptFileName,e);
			throw e;
		} catch (IOException e) {
			error("Erreur lors de la création du fichier de sortie  : "+dirPath + scriptFileName,e);
			throw e;
		}
	}
	/**
	 * Correction des erreurs CC-0010 : L'owner d'un objet n'existe plus
	 * @param dfSession Session Dctm
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrige0010(IDfSession dfSession) throws DfException, IOException {
		info("Analyse des CC-0010");
		if (newOwnername==null || newOwnername.equals("")) {
			error("Le fichier properties ne contient pas de valeur pour "+CCCConstants.DEFAULT_OWNER);
			throw new DfException();
		}

		String dqlSlt="select r_object_id,owner_name,object_name from dm_sysobject (all) where owner_name not in (select user_name from dm_user);";
		String dqlUpd="update dm_sysobject_s set owner_name = '{DEFAULT_OWNER}' where r_object_id ='{ROID}';";
		String scriptFileName=getScriptName(CC10,"sql");
		try {
			IDfCollection coll=CCCUtils.executeQuery(dqlSlt, dfSession);
			String objectId=null;
			String owner_name=null;
			String object_name=null;
			byte[] strBytes = null;
			final StringBuffer sBuf = new StringBuffer();
			FileOutputStream scriptFile= null;
			int compteur=0;
			while(coll.next()) {
				if (scriptFile==null) {
					scriptFile=new FileOutputStream(dirPath + scriptFileName);
				}
				objectId = coll.getString("r_object_id");
				owner_name=coll.getString("owner_name");
				object_name=coll.getString("object_name");
				String logMsg="correction pour changer l'owner_name de l'objet "+object_name+" ("+objectId+") : "+owner_name+" -> "+newOwnername;
				debug(logMsg);
				dqlUpd=dqlUpd.replace("{DEFAULT_OWNER}", newOwnername);
				dqlUpd=dqlUpd.replace("{ROID}", objectId);
				sBuf.append("# ").append(logMsg).append("\n")
				.append(dqlUpd).append("\n");
				compteur++;
			}
			info("\tCC-0010 détecté(s) : "+compteur);
			if (compteur>0) {
				strBytes = sBuf.toString().getBytes();
				scriptFile.write(strBytes);
				scriptFile.close();
				info("\tLe script '"+dirPath+scriptFileName+"' a été créé. Merci de l'exécuter.");
			}
		} catch (DfException e) {
			error("Erreur lors de la génération du script de correction des erreurs CC-0010" ,e);
			throw e;
		} catch (FileNotFoundException e) {
			error("Erreur fichier de sortie non trouvé : "+dirPath + scriptFileName,e);
			throw e;
		} catch (IOException e) {
			error("Erreur lors de la création du fichier de sortie  : "+dirPath + scriptFileName,e);
			throw e;
		}
	}
	/**
	 * Correction des erreurs CC-0046
	 * @param dfSession Session Dctm
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrige0046(IDfSession dfSession) throws DfException, IOException {
		info("Analyse des CC-0046");
		String dql="select distinct r_workflow_id,WF.object_name,r_runtime_state from dmi_package P, dm_workflow WF where  P.r_workflow_id=WF.r_object_id and P.r_component_id not in (select r_object_id from dm_sysobject(all)) enable(row_based)";
		String scriptFileName=getScriptName(CC46,"api");
		try {
			IDfCollection coll=CCCUtils.executeQuery(dql, dfSession);
			String r_workflow_id=null;
			int r_runtime_state=0;
			String object_name=null;
			byte[] strBytes = null;
			final StringBuffer sBuf = new StringBuffer();
			FileOutputStream scriptFile= null;
			int compteur=0;
			while(coll.next()) {
				if (scriptFile==null) {
					scriptFile=new FileOutputStream(dirPath + scriptFileName);
				}
				r_workflow_id = coll.getString("r_workflow_id");
				r_runtime_state = coll.getInt("r_runtime_state");
				object_name=coll.getString("object_name");
				String logMsg="correction pour arreter le workflow "+object_name+" ("+r_workflow_id+") - Etat="+r_runtime_state;
				debug(logMsg);
				sBuf.append("# ").append(logMsg).append("\n");
				if (r_runtime_state==1) {
					sBuf.append("destroy,c,"+r_workflow_id).append("\n");
				} else if (r_runtime_state==1) {
					sBuf.append("abort,c,"+r_workflow_id).append("\n")
					.append("destroy,c,"+r_workflow_id).append("\n");
				}
				compteur++;
			}
			info("\tCC-0046 détecté(s) : "+compteur);
			if (compteur>0) {
				strBytes = sBuf.toString().getBytes();
				scriptFile.write(strBytes);
				scriptFile.close();
				info("\tLe script '"+dirPath+scriptFileName+"' a été créé. Merci de l'exécuter.");
			}
		} catch (DfException e) {
			error("Erreur lors de la génération du script de correction des erreurs CC-0046" ,e);
			throw e;
		} catch (FileNotFoundException e) {
			error("Erreur fichier de sortie non trouvé : "+dirPath + scriptFileName,e);
			throw e;
		} catch (IOException e) {
			error("Erreur lors de la création du fichier de sortie  : "+dirPath + scriptFileName,e);
			throw e;
		}
	}
	/**
	 * Correction des erreurs CC-0081
	 * @param dfSession Session Dctm
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrige0081(IDfSession dfSession) throws DfException, IOException {
		info("Analyse des CC-0081");
		String dql="select r_object_id ,i_supergroups_names,i_position from dm_group_r a where a.i_supergroups_names is not NULL and a.i_supergroups_names <> ' ' and not exists (select b.group_name from dm_group_s b where b.group_name = a.i_supergroups_names);";
		String scriptFileName=getScriptName(CC81,"api");

		try {
			IDfCollection coll=CCCUtils.executeQuery(dql, dfSession);
			String objectId=null;
			String i_supergroups_names=null;
			int position=0;
			byte[] strBytes = null;
			final StringBuffer sBuf = new StringBuffer();
			FileOutputStream scriptFile= null;
			int compteur=0;
			while(coll.next()) {
				if (scriptFile==null) {
					scriptFile=new FileOutputStream(dirPath + scriptFileName);
				}
				objectId = coll.getString("r_object_id");
				position = -coll.getInt("i_position")-1;
				i_supergroups_names=coll.getString("i_supergroups_names");
				String logMsg="correction du groupe Id="+objectId+" : retrait de la valeur '"+i_supergroups_names+"' de l'attribut i_supergroups_names (a la position "+position+")";
				debug(logMsg);
				sBuf.append("# ").append(logMsg).append("\n")
				.append("fetch,c,"+objectId).append("\n")
				.append("remove,c,"+objectId+",i_supergroups_names["+position+"]").append("\n")
				.append("save,c,"+objectId).append("\n");
				compteur++;
			}
			info("\tCC-0081 détecté(s) : "+compteur);
			if (compteur>0) {
				strBytes = sBuf.toString().getBytes();
				scriptFile.write(strBytes);
				scriptFile.close();
				info("\tLe script '"+dirPath+scriptFileName+"' a été créé. Merci de l'exécuter.");
			}
		} catch (DfException e) {
			error("Erreur lors de la génération du script de correction des erreurs CC-0081" ,e);
			throw e;
		} catch (FileNotFoundException e) {
			error("Erreur fichier de sortie non trouvé : "+dirPath + scriptFileName,e);
			throw e;
		} catch (IOException e) {
			error("Erreur lors de la création du fichier de sortie  : "+dirPath + scriptFileName,e);
			throw e;
		}
	}

	/**
	 * Correction des erreurs Consistency Checker
	 * @param dfSession
	 * @throws DfException
	 * @throws IOException
	 */
	private static void corrigeAll(IDfSession dfSession) throws DfException, IOException {
		corrige0007(dfSession);
		corrige0009(dfSession);
		corrige0010(dfSession);
		corrige0046(dfSession);
		corrige0081(dfSession);
	}

	private static void error(String message,Exception t) {
		CCCUtils.error(null, message, t);
	}

	private static void info(String message,Exception t) {
		CCCUtils.info(null, message, t);
	}
	private static void debug(String message,Exception t) {
		CCCUtils.debug(null, message, t);
	}
	private static void debug(String message) {
		debug(message,null);
	}	
	private static void error(String message) {
		error(message,null);
	}	
	private static void info(String message) {
		info(message,null);
	}
	private static void usage() {
		System.out.println("Utilisation : ");
		System.out.println("\t ccc.bat {option}");
		System.out.println("\t\t Valeurs possibles pour {option} : ");
		System.out.println("\t\t ALL     : Correction de toutes les erreurs  (que l'outil sait traiter) listées par le consistency checker");
		System.out.println("\t\t CC-00xx : Correction des erreurs CC-00xx");
		System.out.println("\t\t ou 'xx' peut prendre comme valeur 07 ou 09 ou 10 ou 46 ou 81");
	}
}
